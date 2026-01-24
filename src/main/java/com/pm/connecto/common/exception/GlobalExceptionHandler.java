package com.pm.connecto.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.common.response.ErrorCode;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ApiResponse.error(errorCode, e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
		return ApiResponse.error(ErrorCode.INVALID_INPUT, e.getMessage());
	}

	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
		return ApiResponse.error(ErrorCode.INTERNAL_ERROR, e.getMessage());
	}
}
