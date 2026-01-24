package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

public class UnauthorizedException extends BusinessException {

	public UnauthorizedException(ErrorCode errorCode) {
		super(errorCode);
	}

	public UnauthorizedException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
