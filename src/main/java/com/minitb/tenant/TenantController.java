package com.minitb.tenant;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minitb.tenant.TenantDtos.CreateTenantRequest;
import com.minitb.tenant.TenantDtos.TenantResponse;

import jakarta.validation.Valid;

// System-administrator API: tenants are not scoped to a tenant, so no X-Tenant-Id here.
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

	private final TenantService tenantService;

	public TenantController(TenantService tenantService) {
		this.tenantService = tenantService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
		return TenantResponse.from(tenantService.create(request.title(), request.email()));
	}

	@GetMapping("/{tenantId}")
	public TenantResponse get(@PathVariable UUID tenantId) {
		return TenantResponse.from(tenantService.get(tenantId));
	}

}
