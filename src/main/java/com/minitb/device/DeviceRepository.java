package com.minitb.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

	Optional<Device> findByIdAndTenantId(UUID id, UUID tenantId);

	List<Device> findAllByTenantIdOrderByName(UUID tenantId);

}
