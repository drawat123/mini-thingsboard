package com.minitb.deviceprofile;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DeviceProfileDtos {

	private DeviceProfileDtos() {
	}

	public record CreateDeviceProfileRequest(
			@NotBlank @Size(max = 255) String name,
			String description) {
	}

	public record DeviceProfileResponse(UUID id, long createdTime, UUID tenantId, String name, String description) {

		static DeviceProfileResponse from(DeviceProfile p) {
			return new DeviceProfileResponse(p.getId(), p.getCreatedTime(), p.getTenantId(), p.getName(),
					p.getDescription());
		}

	}

}
