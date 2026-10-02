package com.minitb.deviceprofile;

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
@Table(name = "device_profile")
public class DeviceProfile extends BaseEntity {

	// No setter: a profile can never move to another tenant.
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Setter
	@Column(name = "name", nullable = false)
	private String name;

	@Setter
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	public DeviceProfile(UUID tenantId, String name, String description) {
		this.tenantId = tenantId;
		this.name = name;
		this.description = description;
	}

}
