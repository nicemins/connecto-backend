package com.pm.connecto.match.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.dto.MatchResultResponse;
import com.pm.connecto.match.dto.MatchStartResponse;
import com.pm.connecto.match.dto.MatchStatusResponse;
import com.pm.connecto.match.repository.CallSessionRepository;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.dto.ProfileResponse;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

/**
 * 매칭 서비스 (프로덕션 수준)
 * - Redis 기반 FIFO 매칭 엔진
 * - 동시성 제어 및 보안 강화
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 */
@Service
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class MatchService {

	private static final Logger log = LoggerFactory.getLogger(MatchService.class);

	private final CallSessionRepository callSessionRepository;
	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;
	private final MatchQueueService matchQueueService;

	public MatchService(
		CallSessionRepository callSessionRepository,
		ProfileRepository profileRepository,
		UserRepository userRepository,
		MatchQueueService matchQueueService
	) {
		this.callSessionRepository = callSessionRepository;
		this.profileRepository = profileRepository;
		this.userRepository = userRepository;
		this.matchQueueService = matchQueueService;
	}

	/**
	 * 대기열 진입 및 매칭 시작
	 * - 진행 중인 통화가 있으면 예외 발생
	 * - Redis 대기열에 추가 후 즉시 매칭 시도
	 */
	@Transactional
	public MatchStartResponse startMatching(Long userId) {
		// 진행 중인 통화 확인
		if (callSessionRepository.findInProgressByUserId(userId).isPresent()) {
			log.warn("User {} is already in a call", userId);
			throw new ForbiddenException(ErrorCode.ALREADY_IN_CALL);
		}

		// 대기열 진입
		matchQueueService.enqueue(userId);

		// 즉시 매칭 시도
		Long matchedUserId = matchQueueService.findMatch(userId);

		if (matchedUserId != null) {
			// 매칭 성공
			User user1 = userRepository.findByIdForAuth(userId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
			User user2 = userRepository.findByIdForAuth(matchedUserId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

			// WebRTC 채널 ID 생성
			String webrtcChannelId = generateWebRTCChannelId();

			// 세션 생성
			CallSession session = createMatchedSession(user1, user2, webrtcChannelId);

			log.info("Match successful: User {} matched with User {}, Session ID: {}", 
				userId, matchedUserId, session.getId());

			return MatchStartResponse.matched(session.getId(), webrtcChannelId);
		}

		// 매칭 대기 중
		log.info("User {} is waiting for match", userId);
		return MatchStartResponse.waiting();
	}

	/**
	 * 대기열 이탈
	 */
	@Transactional
	public void cancelMatching(Long userId) {
		matchQueueService.dequeue(userId);
		log.info("User {} cancelled matching", userId);
	}

	/**
	 * 매칭 상태 확인
	 */
	@Transactional(readOnly = true)
	public MatchStatusResponse getMatchStatus(Long userId) {
		return callSessionRepository.findInProgressByUserId(userId)
			.map(session -> {
				log.debug("User {} has active session: {}", userId, session.getId());
				return MatchStatusResponse.matched(session.getId(), session.getWebrtcChannelId());
			})
			.orElseGet(() -> {
				// 대기열에 있는지 확인
				boolean inQueue = matchQueueService.isInQueue(userId);
				if (inQueue) {
					log.debug("User {} is waiting in queue", userId);
				}
				return MatchStatusResponse.waiting();
			});
	}

	/**
	 * 통화 종료 후 상대방 프로필 조회 (보안 강화)
	 * - 세션이 종료된 상태이고, 요청한 사용자가 세션에 참여한 경우에만 허용
	 * - 세션 참여 여부를 엄격히 검증
	 */
	@Transactional(readOnly = true)
	public MatchResultResponse getMatchResult(Long sessionId, Long userId) {
		CallSession session = callSessionRepository.findByIdAndUserId(sessionId, userId)
			.orElseThrow(() -> {
				log.warn("Session {} not found or user {} not authorized", sessionId, userId);
				return new ResourceNotFoundException(ErrorCode.SESSION_NOT_FOUND);
			});

		// 세션이 종료되지 않았으면 접근 불가
		if (!session.isEnded()) {
			log.warn("User {} attempted to access non-ended session {}", userId, sessionId);
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		// 세션 참여 여부 재검증 (이중 체크)
		Long user1Id = session.getUser1().getId();
		Long user2Id = session.getUser2().getId();
		if (!userId.equals(user1Id) && !userId.equals(user2Id)) {
			log.error("Security violation: User {} attempted to access session {} without authorization", 
				userId, sessionId);
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		// 상대방 정보 조회
		User otherUser = session.getOtherUser(userId);
		Profile otherProfile = profileRepository.findByUserId(otherUser.getId())
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROFILE_NOT_FOUND));

		// 재연결 의사 확인
		boolean wantAgain = userId.equals(user1Id) 
			? session.getUser1WantAgain() 
			: session.getUser2WantAgain();

		log.info("User {} retrieved match result for session {}", userId, sessionId);
		return new MatchResultResponse(
			ProfileResponse.from(otherProfile),
			wantAgain
		);
	}

	/**
	 * 매칭 완료 처리 (내부 메서드)
	 * - Redis 매칭 엔진에서 호출
	 */
	@Transactional
	public CallSession createMatchedSession(User user1, User user2, String webrtcChannelId) {
		CallSession session = new CallSession(user1, user2);
		session.start(webrtcChannelId);
		return callSessionRepository.save(session);
	}

	/**
	 * WebRTC 채널 ID 생성
	 * - UUID 기반 고유 채널 ID
	 */
	private String generateWebRTCChannelId() {
		return "channel_" + UUID.randomUUID().toString().replace("-", "");
	}
}
