package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

public class MaxLimitExceededException extends BusinessException {

	public MaxLimitExceededException(ErrorCode errorCode) {
		super(errorCode);
	}

	public MaxLimitExceededException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
