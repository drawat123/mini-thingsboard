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
@Table(name = "device_credentials")
public class DeviceCredentials extends BaseEntity {

	// No setter: credentials always belong to the same device.
	@Column(name = "device_id", nullable = false, updatable = false)
	private UUID deviceId;

	@Setter
	@Column(name = "access_token", nullable = false)
	private String accessToken;

	public DeviceCredentials(UUID deviceId, String accessToken) {
		this.deviceId = deviceId;
		this.accessToken = accessToken;
	}

}
