package com.pm.connecto.health;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 시스템 API
 * - 인증 없이 접근 가능
 * - 서버 상태 확인 (헬스 체크)
 */
@Tag(name = "시스템", description = "서버 상태 확인 및 시스템 API")
@SecurityRequirements  // 인증 불필요 명시 (Swagger UI에서 자물쇠 아이콘 제거)
@RestController
public class HealthController {

	@Operation(summary = "서버 상태 확인", description = "서버 동작 상태를 확인합니다.")
	@ApiResponse(responseCode = "200", description = "서버 정상")
	@GetMapping("/health")
	public Map<String, Object> health() {
		Map<String, Object> response = new HashMap<>();
		response.put("status", "UP");
		return response;
	}
}
