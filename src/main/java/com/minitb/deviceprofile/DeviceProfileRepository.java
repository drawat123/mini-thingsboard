package com.minitb.deviceprofile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceProfileRepository extends JpaRepository<DeviceProfile, UUID> {

	Optional<DeviceProfile> findByIdAndTenantId(UUID id, UUID tenantId);

	List<DeviceProfile> findAllByTenantIdOrderByName(UUID tenantId);

}
