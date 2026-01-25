package com.pm.connecto.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 설정
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

	private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(apiInfo())
			.servers(List.of(
				new Server().url("http://localhost:8080").description("로컬 개발 서버")
			))
			.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
			.components(new Components()
				.addSecuritySchemes(SECURITY_SCHEME_NAME, createSecurityScheme()));
	}

	private Info apiInfo() {
		return new Info()
			.title("Connecto API")
			.description("실시간 언어 교환 매칭 플랫폼 API 문서")
			.version("1.0.0")
			.contact(new Contact()
				.name("Connecto Team")
				.email("support@connecto.com"));
	}

	private SecurityScheme createSecurityScheme() {
		return new SecurityScheme()
			.name(SECURITY_SCHEME_NAME)
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.description("JWT 토큰을 입력하세요. (Bearer 접두사 불필요)");
	}
}
