package com.pm.connecto.call.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.repository.CallSessionRepository;

/**
 * 통화 서비스 (프로덕션 수준)
 * - 통화 종료 처리
 * - 재연결 의사 표현
 * - 로깅 및 모니터링
 */
@Service
public class CallService {

	private static final Logger log = LoggerFactory.getLogger(CallService.class);

	private final CallSessionRepository callSessionRepository;

	public CallService(CallSessionRepository callSessionRepository) {
		this.callSessionRepository = callSessionRepository;
	}

	/**
	 * 통화 종료
	 * - 통화 중인 세션만 종료 가능
	 * - 종료 시간 기록 및 로깅
	 */
	@Transactional
	public void endCall(Long sessionId, Long userId) {
		CallSession session = callSessionRepository.findByIdAndUserId(sessionId, userId)
			.orElseThrow(() -> {
				log.warn("Session {} not found or user {} not authorized", sessionId, userId);
				return new ResourceNotFoundException(ErrorCode.SESSION_NOT_FOUND);
			});

		// 통화 중인 세션만 종료 가능
		if (!session.isInProgress()) {
			log.warn("User {} attempted to end non-in-progress session {}", userId, sessionId);
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		session.end();
		
		// 통화 시간 계산 및 로깅
		if (session.getStartedAt() != null) {
			long durationSeconds = java.time.Duration.between(
				session.getStartedAt(), 
				session.getEndedAt()
			).getSeconds();
			
			log.info("Call ended: Session {}, Duration: {} seconds, Users: {} and {}", 
				sessionId, durationSeconds, session.getUser1().getId(), session.getUser2().getId());
		} else {
			log.warn("Call ended without start time: Session {}", sessionId);
		}
	}

	/**
	 * 재연결 의사 표현 (👍)
	 * - 종료된 세션만 재연결 의사 표현 가능
	 * - 양측 모두 👍를 누른 경우 재연결 처리
	 */
	@Transactional
	public void expressCallAgain(Long sessionId, Long userId, boolean wantAgain) {
		CallSession session = callSessionRepository.findByIdAndUserId(sessionId, userId)
			.orElseThrow(() -> {
				log.warn("Session {} not found or user {} not authorized", sessionId, userId);
				return new ResourceNotFoundException(ErrorCode.SESSION_NOT_FOUND);
			});

		// 종료된 세션만 재연결 의사 표현 가능
		if (!session.isEnded()) {
			log.warn("User {} attempted to express call again for non-ended session {}", userId, sessionId);
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		// 사용자별 재연결 의사 설정
		boolean wasBothWantAgain = session.bothWantAgain();
		
		if (userId.equals(session.getUser1().getId())) {
			session.setUser1WantAgain(wantAgain);
		} else if (userId.equals(session.getUser2().getId())) {
			session.setUser2WantAgain(wantAgain);
		} else {
			log.error("Security violation: User {} attempted to modify session {} without authorization", 
				userId, sessionId);
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		log.info("User {} expressed call again preference: {} for session {}", userId, wantAgain, sessionId);

		// 양측 모두 👍를 누른 경우 재연결 처리
		if (!wasBothWantAgain && session.bothWantAgain()) {
			log.info("Both users want to reconnect: Session {}, Users: {} and {}", 
				sessionId, session.getUser1().getId(), session.getUser2().getId());
			// TODO: 재연결 로직 구현 (친구 맺기 또는 다시 통화)
			// - 친구 관계 생성 또는 재매칭 트리거
		}
	}
}
