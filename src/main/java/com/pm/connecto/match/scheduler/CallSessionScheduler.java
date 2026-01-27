package com.pm.connecto.match.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.domain.CallSessionStatus;
import com.pm.connecto.match.repository.CallSessionRepository;
import com.pm.connecto.match.service.MatchQueueService;

/**
 * 통화 세션 스케줄러
 * - 5분 초과 통화 자동 종료
 * - 타임아웃된 대기열 사용자 정리
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 */
@Component
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class CallSessionScheduler {

	private static final Logger log = LoggerFactory.getLogger(CallSessionScheduler.class);
	private static final int MAX_CALL_DURATION_MINUTES = 5;

	private final CallSessionRepository callSessionRepository;
	private final MatchQueueService matchQueueService;

	public CallSessionScheduler(
		CallSessionRepository callSessionRepository,
		MatchQueueService matchQueueService
	) {
		this.callSessionRepository = callSessionRepository;
		this.matchQueueService = matchQueueService;
	}

	/**
	 * 5분 초과 통화 자동 종료
	 * - 1분마다 실행
	 */
	@Scheduled(fixedRate = 60000) // 1분
	@Transactional
	public void expireLongCalls() {
		LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(MAX_CALL_DURATION_MINUTES);
		
		// 인덱스를 활용한 효율적인 쿼리
		List<CallSession> expiredSessions = callSessionRepository.findInProgressSessionsStartedBefore(
			CallSessionStatus.IN_PROGRESS,
			cutoffTime
		);

		for (CallSession session : expiredSessions) {
			session.end();
			
			long durationSeconds = java.time.Duration.between(
				session.getStartedAt(), 
				LocalDateTime.now()
			).getSeconds();
			
			log.info("Auto-expired call session {} (duration: {} seconds, exceeded {} minutes limit)", 
				session.getId(), durationSeconds, MAX_CALL_DURATION_MINUTES);
			
			// TODO: 프론트엔드에 종료 이벤트 전송 (WebSocket 또는 SSE)
			// - WebSocket: /topic/call/{sessionId}/expired
			// - 또는 SSE: /events/call/{sessionId}
		}

		if (!expiredSessions.isEmpty()) {
			log.info("Expired {} long-running call sessions", expiredSessions.size());
		}
	}

	/**
	 * 타임아웃된 대기열 사용자 정리
	 * - 5분마다 실행
	 */
	@Scheduled(fixedRate = 300000) // 5분
	public void cleanupExpiredQueueUsers() {
		try {
			matchQueueService.cleanupExpiredUsers();
		} catch (Exception e) {
			log.error("Error cleaning up expired queue users", e);
		}
	}
}
