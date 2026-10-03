package com.minitb.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minitb.common.CacheConfig;
import com.minitb.common.NotFoundException;
import com.minitb.deviceprofile.DeviceProfileService;

@Service
public class DeviceService {

	private final DeviceRepository deviceRepository;
	private final DeviceCredentialsRepository deviceCredentialsRepository;
	private final DeviceProfileService deviceProfileService;
	private final AccessTokenGenerator accessTokenGenerator;
	private final ApplicationEventPublisher eventPublisher;

	public DeviceService(DeviceRepository deviceRepository, DeviceCredentialsRepository deviceCredentialsRepository,
			DeviceProfileService deviceProfileService, AccessTokenGenerator accessTokenGenerator,
			ApplicationEventPublisher eventPublisher) {
		this.deviceRepository = deviceRepository;
		this.deviceCredentialsRepository = deviceCredentialsRepository;
		this.deviceProfileService = deviceProfileService;
		this.accessTokenGenerator = accessTokenGenerator;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Device create(UUID tenantId, UUID deviceProfileId, String name, String label) {
		// Tenant isolation: throws NotFoundException if the profile belongs to another tenant.
		deviceProfileService.get(tenantId, deviceProfileId);
		Device device = deviceRepository.save(new Device(tenantId, deviceProfileId, name, label));
		// Same transaction: if this insert fails, the device insert is rolled back too.
		deviceCredentialsRepository.save(new DeviceCredentials(device.getId(), accessTokenGenerator.generate()));
		return device;
	}

	@Transactional(readOnly = true)
	public Device get(UUID tenantId, UUID deviceId) {
		return deviceRepository.findByIdAndTenantId(deviceId, tenantId)
				.orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
	}

	@Transactional(readOnly = true)
	public List<Device> list(UUID tenantId) {
		return deviceRepository.findAllByTenantIdOrderByName(tenantId);
	}

	@Transactional(readOnly = true)
	public DeviceCredentials getCredentials(UUID tenantId, UUID deviceId) {
		return findCredentials(tenantId, deviceId);
	}

	@Transactional
	public DeviceCredentials regenerateCredentials(UUID tenantId, UUID deviceId) {
		DeviceCredentials credentials = findCredentials(tenantId, deviceId);
		String oldAccessToken = credentials.getAccessToken();
		// No save() needed: Hibernate detects the change on this managed entity and UPDATEs at commit.
		credentials.setAccessToken(accessTokenGenerator.generate());
		// Handled after commit by DeviceCredentialsCacheEvictor, which removes the old token from the cache.
		eventPublisher.publishEvent(new DeviceCredentialsChangedEvent(oldAccessToken));
		return credentials;
	}

	// The hot path: every telemetry message is authenticated with this lookup (Phase 4).
	// Unknown tokens return empty and are not cached.
	@Cacheable(cacheNames = CacheConfig.DEVICE_CREDENTIALS, key = "#accessToken", unless = "#result == null")
	@Transactional(readOnly = true)
	public Optional<DeviceIdentity> findByAccessToken(String accessToken) {
		return deviceCredentialsRepository.findIdentityByAccessToken(accessToken);
	}

	private DeviceCredentials findCredentials(UUID tenantId, UUID deviceId) {
		get(tenantId, deviceId); // tenant isolation: 404 if the device is not this tenant's
		return deviceCredentialsRepository.findByDeviceId(deviceId)
				.orElseThrow(() -> new NotFoundException("Credentials not found for device: " + deviceId));
	}

}
