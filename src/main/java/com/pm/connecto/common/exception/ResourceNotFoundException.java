package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ResourceNotFoundException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
