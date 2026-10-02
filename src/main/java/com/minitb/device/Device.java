package com.minitb.device;

import java.util.UUID;

import com.minitb.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "device")
public class Device extends BaseEntity {

	// No setter: a device can never move to another tenant.
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Setter
	@Column(name = "device_profile_id", nullable = false)
	private UUID deviceProfileId;

	@Setter
	@Column(name = "name", nullable = false)
	private String name;

	@Setter
	@Column(name = "label")
	private String label;

	public Device(UUID tenantId, UUID deviceProfileId, String name, String label) {
		this.tenantId = tenantId;
		this.deviceProfileId = deviceProfileId;
		this.name = name;
		this.label = label;
	}

}
