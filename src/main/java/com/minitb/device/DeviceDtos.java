package com.minitb.device;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeviceDtos {

	private DeviceDtos() {
	}

	public record CreateDeviceRequest(
			@NotBlank @Size(max = 255) String name,
			@Size(max = 255) String label,
			@NotNull UUID deviceProfileId) {
	}

	public record DeviceResponse(UUID id, long createdTime, UUID tenantId, UUID deviceProfileId, String name,
			String label) {

		static DeviceResponse from(Device d) {
			return new DeviceResponse(d.getId(), d.getCreatedTime(), d.getTenantId(), d.getDeviceProfileId(),
					d.getName(), d.getLabel());
		}

	}

	public record DeviceCredentialsResponse(UUID deviceId, String accessToken) {

		static DeviceCredentialsResponse from(DeviceCredentials c) {
			return new DeviceCredentialsResponse(c.getDeviceId(), c.getAccessToken());
		}

	}

}
