package com.minitb.tenant;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minitb.common.NotFoundException;

@Service
public class TenantService {

	private final TenantRepository tenantRepository;

	public TenantService(TenantRepository tenantRepository) {
		this.tenantRepository = tenantRepository;
	}

	@Transactional
	public Tenant create(String title, String email) {
		return tenantRepository.save(new Tenant(title, email));
	}

	@Transactional(readOnly = true)
	public Tenant get(UUID tenantId) {
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new NotFoundException("Tenant not found: " + tenantId));
	}

}
