package com.minitb.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	private static final String TENANT_SCHEME = "tenant";

	@Bean
	public OpenAPI openApi() {
		// Adds an "Authorize" button to Swagger UI that sends X-Tenant-Id with every request.
		SecurityScheme tenantHeader = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name("X-Tenant-Id");
		return new OpenAPI()
				.info(new Info().title("Mini ThingsBoard API").version("v1"))
				.components(new Components().addSecuritySchemes(TENANT_SCHEME, tenantHeader))
				.addSecurityItem(new SecurityRequirement().addList(TENANT_SCHEME));
	}

}
