package com.minitb.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.minitb.common.CacheConfig;
import com.minitb.common.NotFoundException;
import com.minitb.deviceprofile.DeviceProfile;
import com.minitb.deviceprofile.DeviceProfileService;
import com.minitb.tenant.TenantService;

// Runs against the real Postgres and Redis from docker-compose (docker compose up -d postgres redis).
@SpringBootTest
class DeviceCredentialsCacheTests {

	@Autowired
	private TenantService tenantService;

	@Autowired
	private DeviceProfileService deviceProfileService;

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private CacheManager cacheManager;

	private UUID tenantId;
	private Device device;
	private Cache credentialsCache;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		tenantId = tenantService.create("cache-test-" + suffix, null).getId();
		UUID profileId = deviceProfileService.create(tenantId, "profile", null).getId();
		device = deviceService.create(tenantId, profileId, "device", null);
		credentialsCache = cacheManager.getCache(CacheConfig.DEVICE_CREDENTIALS);
	}

	@Test
	void lookupByTokenIsCached() {
		String token = deviceService.getCredentials(tenantId, device.getId()).getAccessToken();

		assertThat(deviceService.findByAccessToken(token))
				.contains(new DeviceIdentity(device.getId(), tenantId));
		assertThat(credentialsCache.get(token, DeviceIdentity.class))
				.isEqualTo(new DeviceIdentity(device.getId(), tenantId));
	}

	@Test
	void regeneratingEvictsOldTokenAfterCommit() {
		String oldToken = deviceService.getCredentials(tenantId, device.getId()).getAccessToken();
		deviceService.findByAccessToken(oldToken); // warm the cache

		String newToken = deviceService.regenerateCredentials(tenantId, device.getId()).getAccessToken();

		assertThat(credentialsCache.get(oldToken)).isNull();
		assertThat(deviceService.findByAccessToken(oldToken)).isEmpty();
		assertThat(deviceService.findByAccessToken(newToken)).isPresent();
	}

	@Test
	void unknownTokenIsNotCached() {
		String unknown = "no-such-token-" + UUID.randomUUID();

		assertThat(deviceService.findByAccessToken(unknown)).isEmpty();
		assertThat(credentialsCache.get(unknown)).isNull();
	}

	@Test
	void profileCacheIsScopedToTenant() {
		UUID profileId = device.getDeviceProfileId();
		deviceProfileService.get(tenantId, profileId); // cache it

		Cache profiles = cacheManager.getCache(CacheConfig.DEVICE_PROFILES);
		assertThat(profiles.get(tenantId + ":" + profileId, DeviceProfile.class))
				.extracting(DeviceProfile::getTenantId)
				.isEqualTo(tenantId);

		// Another tenant asking for the same id uses a different key, so it still gets a 404 from the DB.
		UUID otherTenant = tenantService.create("cache-other-" + UUID.randomUUID(), null).getId();
		assertThatThrownBy(() -> deviceProfileService.get(otherTenant, profileId))
				.isInstanceOf(NotFoundException.class);
	}

}
