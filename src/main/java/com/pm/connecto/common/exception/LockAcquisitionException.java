package com.pm.connecto.common.exception;

import com.pm.connecto.common.response.ErrorCode;

/**
 * 분산 락 획득 실패 예외
 */
public class LockAcquisitionException extends BusinessException {

	public LockAcquisitionException() {
		super(ErrorCode.REDIS_ERROR, "분산 락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
	}

	public LockAcquisitionException(String message) {
		super(ErrorCode.REDIS_ERROR, message);
	}
}
