package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

public class DuplicateResourceException extends BusinessException {

	public DuplicateResourceException(ErrorCode errorCode) {
		super(errorCode);
	}

	public DuplicateResourceException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
