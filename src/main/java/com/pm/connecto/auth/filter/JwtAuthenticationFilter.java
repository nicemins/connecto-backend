package com.pm.connecto.auth.filter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String USER_ID_ATTRIBUTE = "userId";

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
		this.objectMapper = new ObjectMapper();
		this.objectMapper.registerModule(new JavaTimeModule());
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String token = extractToken(request);

		if (token != null) {
			if (!jwtTokenProvider.validateToken(token)) {
				sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.INVALID_TOKEN);
				return;
			}

			Long userId = jwtTokenProvider.getUserIdFromToken(token);
			Optional<User> userOptional = userRepository.findByIdForAuth(userId);

			if (userOptional.isEmpty()) {
				sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.USER_NOT_FOUND);
				return;
			}

			User user = userOptional.get();

			// 1. deletedAt 확인 (Soft Delete)
			if (user.getDeletedAt() != null) {
				sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.DELETED_USER);
				return;
			}

			// 2. status != ACTIVE 확인
			if (!user.isActive()) {
				if (user.isBlocked()) {
					sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.BLOCKED_USER);
				} else if (user.isDeleted()) {
					sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.DELETED_USER);
				} else {
					// 기타 비활성 상태 (SUSPENDED, PENDING 등 향후 확장 대비)
					sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED);
				}
				return;
			}

			request.setAttribute(USER_ID_ATTRIBUTE, userId);
		}

		filterChain.doFilter(request, response);
	}

	private String extractToken(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(BEARER_PREFIX.length());
		}
		return null;
	}

	private void sendErrorResponse(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		ErrorResponse errorResponse = new ErrorResponse(
			false,
			errorCode.getCode(),
			errorCode.getMessage(),
			LocalDateTime.now()
		);

		response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
	}

	private record ErrorResponse(boolean success, String code, String message, LocalDateTime timestamp) {}
}
