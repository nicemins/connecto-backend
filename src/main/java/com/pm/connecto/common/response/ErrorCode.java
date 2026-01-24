package com.pm.connecto.common.response;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	// 400 Bad Request
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력값입니다."),
	MAX_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "MAX_LIMIT_EXCEEDED", "최대 허용 개수를 초과했습니다."),

	// 401 Unauthorized
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
	DELETED_USER(HttpStatus.UNAUTHORIZED, "DELETED_USER", "탈퇴한 사용자입니다."),
	INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),

	// 403 Forbidden
	BLOCKED_USER(HttpStatus.FORBIDDEN, "BLOCKED_USER", "차단된 사용자입니다."),
	INACTIVE_USER(HttpStatus.FORBIDDEN, "INACTIVE_USER", "비활성 계정입니다."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다."),

	// 404 Not Found
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
	PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "프로필을 찾을 수 없습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다."),
	LANGUAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "LANGUAGE_NOT_FOUND", "존재하지 않는 언어입니다."),
	INTEREST_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEREST_NOT_FOUND", "존재하지 않는 관심사입니다."),

	// 409 Conflict
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 존재하는 이메일입니다."),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 존재하는 닉네임입니다."),
	DUPLICATE_PROFILE(HttpStatus.CONFLICT, "DUPLICATE_PROFILE", "이미 프로필이 존재합니다."),
	DUPLICATE_LANGUAGE(HttpStatus.CONFLICT, "DUPLICATE_LANGUAGE", "이미 등록된 언어입니다."),
	DUPLICATE_INTEREST(HttpStatus.CONFLICT, "DUPLICATE_INTEREST", "이미 존재하는 관심사입니다."),

	// 500 Internal Server Error
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
