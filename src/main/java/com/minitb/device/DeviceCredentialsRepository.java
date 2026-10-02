package com.minitb.device;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialsRepository extends JpaRepository<DeviceCredentials, UUID> {

	Optional<DeviceCredentials> findByDeviceId(UUID deviceId);

}
