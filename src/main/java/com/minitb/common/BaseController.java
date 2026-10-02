package com.minitb.common;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseController {

	// TEMPORARY: stands in for the authenticated user until we add Spring Security.
	// Then this will read the tenant from the SecurityContext, like ThingsBoard's getTenantId().
	private static final String TENANT_HEADER = "X-Tenant-Id";

	@Autowired
	private HttpServletRequest request;

	protected UUID getCurrentTenantId() {
		String header = request.getHeader(TENANT_HEADER);
		if (header == null || header.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing " + TENANT_HEADER + " header");
		}
		try {
			return UUID.fromString(header);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid " + TENANT_HEADER + " header");
		}
	}

}
