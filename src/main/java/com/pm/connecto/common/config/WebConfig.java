package com.pm.connecto.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 설정 (CORS 등)
 * 
 * <p>CORS 설정:
 * <ul>
 *   <li>개발 환경: http://localhost:3000</li>
 *   <li>프로덕션: 환경변수 FRONTEND_URL로 설정</li>
 *   <li>쿠키 기반 인증 지원 (credentials: true)</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${cors.allowed-origins:http://localhost:3000}")
	private String[] allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.exposedHeaders("Set-Cookie", "Authorization")
			.allowCredentials(true)
			.maxAge(3600);  // 1시간 동안 preflight 캐시
	}
}
