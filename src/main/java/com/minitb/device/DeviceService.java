package com.minitb.device;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minitb.common.NotFoundException;
import com.minitb.deviceprofile.DeviceProfileService;

@Service
public class DeviceService {

	private final DeviceRepository deviceRepository;
	private final DeviceCredentialsRepository deviceCredentialsRepository;
	private final DeviceProfileService deviceProfileService;
	private final AccessTokenGenerator accessTokenGenerator;

	public DeviceService(DeviceRepository deviceRepository, DeviceCredentialsRepository deviceCredentialsRepository,
			DeviceProfileService deviceProfileService, AccessTokenGenerator accessTokenGenerator) {
		this.deviceRepository = deviceRepository;
		this.deviceCredentialsRepository = deviceCredentialsRepository;
		this.deviceProfileService = deviceProfileService;
		this.accessTokenGenerator = accessTokenGenerator;
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
		// No save() needed: Hibernate detects the change on this managed entity and UPDATEs at commit.
		credentials.setAccessToken(accessTokenGenerator.generate());
		return credentials;
	}

	private DeviceCredentials findCredentials(UUID tenantId, UUID deviceId) {
		get(tenantId, deviceId); // tenant isolation: 404 if the device is not this tenant's
		return deviceCredentialsRepository.findByDeviceId(deviceId)
				.orElseThrow(() -> new NotFoundException("Credentials not found for device: " + deviceId));
	}

}
