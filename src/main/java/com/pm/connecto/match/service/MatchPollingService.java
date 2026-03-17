package com.pm.connecto.match.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.pm.connecto.match.dto.MatchStartResponse;

/**
 * 매칭 폴링 서비스
 * - 프론트엔드에서 주기적으로 호출하여 매칭 상태 확인
 * - 대기 중인 사용자에게 매칭 완료 알림
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 * 
 * TODO: WebSocket 또는 SSE로 실시간 알림 구현 권장
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class MatchPollingService {

	private static final Logger log = LoggerFactory.getLogger(MatchPollingService.class);

	private final MatchService matchService;

	public MatchPollingService(MatchService matchService) {
		this.matchService = matchService;
	}

	/**
	 * 비동기 매칭 재시도
	 * - 대기 중인 사용자에게 주기적으로 매칭 시도
	 */
	@Async
	public void retryMatching(Long userId) {
		try {
			MatchStartResponse response = matchService.startMatching(userId);
			if (response.matched()) {
				log.info("Match found for user {} via polling", userId);
			}
		} catch (Exception e) {
			log.error("Error during match retry for user {}", userId, e);
		}
	}
}
