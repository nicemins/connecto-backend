package com.pm.connecto.common.context;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 현재 요청의 사용자 정보를 관리하는 컨텍스트
 * - Request Scope: 요청당 하나의 인스턴스
 * - JwtAuthenticationFilter에서 설정한 userId를 조회
 * - 추후 Spring Security 적용 시 SecurityContextHolder로 교체 가능
 * 
 * <p>Spring Security 마이그레이션 시:
 * <pre>
 * public Long getUserId() {
 *     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *     if (auth == null || !auth.isAuthenticated()) {
 *         throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
 *     }
 *     return ((CustomUserDetails) auth.getPrincipal()).getId();
 * }
 * </pre>
 */
@Component
@RequestScope
public class UserContext {

	private static final String USER_ID_ATTRIBUTE = "userId";

	private final HttpServletRequest request;

	public UserContext(HttpServletRequest request) {
		this.request = request;
	}

	/**
	 * 현재 로그인한 사용자 ID 조회
	 * - JWT 필터에서 설정한 userId 반환
	 * - 인증되지 않은 경우 UnauthorizedException 발생
	 * 
	 * @return 인증된 사용자 ID
	 * @throws UnauthorizedException 인증되지 않은 경우
	 */
	public Long getUserId() {
		Long userId = (Long) request.getAttribute(USER_ID_ATTRIBUTE);

		if (userId == null) {
			throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
		}

		return userId;
	}

	/**
	 * 현재 로그인 여부 확인
	 * 
	 * @return 인증된 경우 true
	 */
	public boolean isAuthenticated() {
		return request.getAttribute(USER_ID_ATTRIBUTE) != null;
	}

	/**
	 * 현재 사용자 ID 조회 (null 허용)
	 * - 비로그인 사용자도 접근 가능한 API용
	 * - 인증되지 않은 경우 null 반환
	 * 
	 * @return 인증된 사용자 ID 또는 null
	 */
	public Long getUserIdOrNull() {
		return (Long) request.getAttribute(USER_ID_ATTRIBUTE);
	}
}
