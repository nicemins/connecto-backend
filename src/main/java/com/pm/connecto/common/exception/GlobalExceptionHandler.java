package com.pm.connecto.common.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.common.response.ErrorCode;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 비즈니스 예외 처리
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ApiResponse.error(errorCode, e.getMessage()));
	}

	/**
	 * @Valid @RequestBody 검증 실패 시 발생
	 * - DTO 필드 검증 오류
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<List<ValidationError>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		List<ValidationError> errors = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ValidationError(
				error.getField(),
				error.getDefaultMessage(),
				error.getRejectedValue()
			))
			.toList();

		String message = errors.stream()
			.map(ValidationError::message)
			.collect(Collectors.joining(", "));

		return ApiResponse.error(ErrorCode.INVALID_INPUT, message, errors);
	}

	/**
	 * @Valid @RequestParam / @PathVariable 검증 실패 시 발생
	 * - 컨트롤러 파라미터 검증 오류
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<List<ValidationError>> handleConstraintViolationException(ConstraintViolationException e) {
		List<ValidationError> errors = e.getConstraintViolations()
			.stream()
			.map(violation -> {
				String field = violation.getPropertyPath().toString();
				// 메서드명 제거 (예: checkEmailExists.email -> email)
				if (field.contains(".")) {
					field = field.substring(field.lastIndexOf('.') + 1);
				}
				return new ValidationError(
					field,
					violation.getMessage(),
					violation.getInvalidValue()
				);
			})
			.toList();

		String message = errors.stream()
			.map(ValidationError::message)
			.collect(Collectors.joining(", "));

		return ApiResponse.error(ErrorCode.INVALID_INPUT, message, errors);
	}

	/**
	 * Spring 6.1+ 에서 @Valid @RequestParam 검증 실패 시 발생
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
		String message = e.getAllErrors()
			.stream()
			.map(error -> error.getDefaultMessage())
			.collect(Collectors.joining(", "));

		return ApiResponse.error(ErrorCode.INVALID_INPUT, message);
	}

	/**
	 * 필수 요청 파라미터 누락 시 발생
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
		String message = String.format("필수 파라미터 '%s'이(가) 누락되었습니다.", e.getParameterName());
		return ApiResponse.error(ErrorCode.INVALID_INPUT, message);
	}

	/**
	 * 잘못된 인자 예외
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
		return ApiResponse.error(ErrorCode.INVALID_INPUT, e.getMessage());
	}

	/**
	 * 기타 런타임 예외 (최후 방어선)
	 */
	@ExceptionHandler(RuntimeException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
		// 프로덕션에서는 상세 메시지 노출 금지
		return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다.");
	}

	/**
	 * Validation 오류 상세 정보
	 */
	public record ValidationError(
		String field,
		String message,
		Object rejectedValue
	) {
	}
}
