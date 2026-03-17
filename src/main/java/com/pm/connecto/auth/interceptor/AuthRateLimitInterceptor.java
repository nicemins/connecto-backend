package com.pm.connecto.auth.interceptor;

import java.io.IOException;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증 엔드포인트 Rate Limiting 인터셉터
 *
 * <p>Redis를 이용해 IP 기준으로 요청 수를 제한합니다.
 * Redis가 없는 환경(로컬 개발)에서는 자동으로 비활성화됩니다.
 *
 * <p>제한 기준:
 * <ul>
 *   <li>/auth/login, /auth/social-login: 분당 10회</li>
 *   <li>/auth/signup: 시간당 5회</li>
 * </ul>
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

	// Lua 스크립트로 INCR+EXPIRE 원자 실행 (pExpire 버그 우회)
	// TTL이 -1(무기한)인 경우에도 EXPIRE를 설정해 영구 차단 방지
	private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
		"local c = redis.call('INCR', KEYS[1]); " +
		"if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
		"elseif redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; " +
		"return c",
		Long.class
	);

	@Autowired(required = false)
	private RedisTemplate<String, String> redisTemplate;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
		if (redisTemplate == null) {
			return true;
		}

		String uri = request.getRequestURI();
		String ip = getClientIp(request);

		if ("/auth/login".equals(uri) || "/auth/social-login".equals(uri)) {
			return checkLimit(ip, "login", 10, 60, response);
		}
		if ("/auth/signup".equals(uri)) {
			return checkLimit(ip, "signup", 5, 3600, response);
		}

		return true;
	}

	private boolean checkLimit(String ip, String action, int limit, long windowSeconds, HttpServletResponse response) throws IOException {
		String key = "rate:" + action + ":" + ip;
		Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key), String.valueOf(windowSeconds));
		if (count != null && count > limit) {
			response.setStatus(429);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
			return false;
		}
		return true;
	}

	private String getClientIp(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			return xForwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
