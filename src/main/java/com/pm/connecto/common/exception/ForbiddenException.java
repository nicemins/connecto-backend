package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

public class ForbiddenException extends BusinessException {

	public ForbiddenException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ForbiddenException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
