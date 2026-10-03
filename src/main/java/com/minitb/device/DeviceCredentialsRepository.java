package com.minitb.device;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceCredentialsRepository extends JpaRepository<DeviceCredentials, UUID> {

	Optional<DeviceCredentials> findByDeviceId(UUID deviceId);

	@Query("""
			SELECT new com.minitb.device.DeviceIdentity(d.id, d.tenantId)
			FROM DeviceCredentials c JOIN Device d ON d.id = c.deviceId
			WHERE c.accessToken = :accessToken""")
	Optional<DeviceIdentity> findIdentityByAccessToken(String accessToken);

}
