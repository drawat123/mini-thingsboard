package com.minitb.deviceprofile;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minitb.common.CacheConfig;
import com.minitb.common.NotFoundException;

@Service
public class DeviceProfileService {

	private final DeviceProfileRepository deviceProfileRepository;

	public DeviceProfileService(DeviceProfileRepository deviceProfileRepository) {
		this.deviceProfileRepository = deviceProfileRepository;
	}

	@Transactional
	public DeviceProfile create(UUID tenantId, String name, String description) {
		return deviceProfileRepository.save(new DeviceProfile(tenantId, name, description));
	}

	// The tenant is part of the key, so a cached profile can never be returned to another tenant.
	// Any future update/delete of a profile must evict this same key.
	@Cacheable(cacheNames = CacheConfig.DEVICE_PROFILES, key = "#tenantId + ':' + #profileId")
	@Transactional(readOnly = true)
	public DeviceProfile get(UUID tenantId, UUID profileId) {
		return deviceProfileRepository.findByIdAndTenantId(profileId, tenantId)
				.orElseThrow(() -> new NotFoundException("Device profile not found: " + profileId));
	}

	@Transactional(readOnly = true)
	public List<DeviceProfile> list(UUID tenantId) {
		return deviceProfileRepository.findAllByTenantIdOrderByName(tenantId);
	}

}
