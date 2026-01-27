package com.pm.connecto.common.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

	@Schema(description = "성공 여부", example = "true")
	private boolean success;

	@Schema(description = "에러 코드 (실패 시)", example = "USER_NOT_FOUND")
	private String code;

	@Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
	private String message;

	@Schema(description = "응답 데이터")
	private T data;

	@Schema(description = "Validation 에러 상세 목록 (실패 시)")
	private Object errors;

	@Schema(description = "응답 시간", example = "2024-01-23T10:30:00")
	private LocalDateTime timestamp;

	private ApiResponse(boolean success, String code, String message, T data, Object errors) {
		this.success = success;
		this.code = code;
		this.message = message;
		this.data = data;
		this.errors = errors;
		this.timestamp = LocalDateTime.now();
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, null, null, data, null);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, null, message, data, null);
	}

	public static <T> ApiResponse<T> error(String message) {
		return new ApiResponse<>(false, null, message, null, null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, errorCode.getCode(), message, null, null);
	}

	/**
	 * Validation 오류 시 상세 에러 목록 포함
	 */
	public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, Object errors) {
		return new ApiResponse<>(false, errorCode.getCode(), message, null, errors);
	}

	public boolean isSuccess() {
		return success;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public T getData() {
		return data;
	}

	public Object getErrors() {
		return errors;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}
}
