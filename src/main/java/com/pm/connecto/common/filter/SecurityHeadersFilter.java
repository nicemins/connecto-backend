package com.pm.connecto.common.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 보안 응답 헤더 필터 (OWASP A05 Security Misconfiguration 대응)
 *
 * <p>모든 HTTP 응답에 다음 보안 헤더를 추가합니다:
 * <ul>
 *   <li>X-Content-Type-Options: nosniff — MIME 스니핑 방지</li>
 *   <li>X-Frame-Options: DENY — 클릭재킹 방지</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin — Referrer 노출 최소화</li>
 *   <li>Content-Security-Policy: default-src 'none' — CSP 기본 정책</li>
 *   <li>Strict-Transport-Security — HTTPS 강제 (프로덕션용)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
		throws IOException, ServletException {
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		httpResponse.setHeader("X-Content-Type-Options", "nosniff");
		httpResponse.setHeader("X-Frame-Options", "DENY");
		httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
		httpResponse.setHeader("Content-Security-Policy", "default-src 'none'");
		httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
		chain.doFilter(request, response);
	}
}
