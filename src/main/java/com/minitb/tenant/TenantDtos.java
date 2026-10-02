package com.minitb.tenant;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TenantDtos {

	private TenantDtos() {
	}

	public record CreateTenantRequest(
			@NotBlank @Size(max = 255) String title,
			@Email @Size(max = 255) String email) {
	}

	public record TenantResponse(UUID id, long createdTime, String title, String email) {

		static TenantResponse from(Tenant t) {
			return new TenantResponse(t.getId(), t.getCreatedTime(), t.getTitle(), t.getEmail());
		}

	}

}
