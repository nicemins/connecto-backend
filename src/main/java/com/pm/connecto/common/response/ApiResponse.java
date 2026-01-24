package com.pm.connecto.common.response;

import java.time.LocalDateTime;

public class ApiResponse<T> {

	private boolean success;
	private String code;
	private String message;
	private T data;
	private LocalDateTime timestamp;

	private ApiResponse(boolean success, String code, String message, T data) {
		this.success = success;
		this.code = code;
		this.message = message;
		this.data = data;
		this.timestamp = LocalDateTime.now();
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, null, null, data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, null, message, data);
	}

	public static <T> ApiResponse<T> error(String message) {
		return new ApiResponse<>(false, null, message, null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, errorCode.getCode(), message, null);
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

	public LocalDateTime getTimestamp() {
		return timestamp;
	}
}
