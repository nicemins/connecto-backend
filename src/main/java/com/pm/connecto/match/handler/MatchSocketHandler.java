package com.pm.connecto.match.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.repository.CallSessionRepository;
import com.pm.connecto.match.service.MatchQueueService;
import com.pm.connecto.match.service.MatchService;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Socket.io 매칭 핸들러
 * - 실시간 매칭 이벤트 처리
 * - 클라이언트 연결/해제 관리
 * - 매칭 시작/취소 이벤트 처리
 */
@Component
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class MatchSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MatchSocketHandler.class);

	private final SocketIOServer socketIOServer;
	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;
	private final MatchService matchService;
	private final MatchQueueService matchQueueService;
	private final CallSessionRepository callSessionRepository;

	// 클라이언트별 사용자 ID 매핑
	private final Map<String, Long> clientUserIdMap = new ConcurrentHashMap<>();

	public MatchSocketHandler(
		SocketIOServer socketIOServer,
		JwtTokenProvider jwtTokenProvider,
		UserRepository userRepository,
		MatchService matchService,
		MatchQueueService matchQueueService,
		CallSessionRepository callSessionRepository
	) {
		this.socketIOServer = socketIOServer;
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
		this.matchService = matchService;
		this.matchQueueService = matchQueueService;
		this.callSessionRepository = callSessionRepository;
	}

	@PostConstruct
	public void start() {
		socketIOServer.start();
		log.info("Socket.io server started on port {}", socketIOServer.getConfiguration().getPort());
	}

	@PreDestroy
	public void stop() {
		socketIOServer.stop();
		log.info("Socket.io server stopped");
	}

	/**
	 * 클라이언트 연결 시 인증 처리
	 */
	@OnConnect
	public void onConnect(SocketIOClient client) {
		try {
			// 쿼리 파라미터 또는 auth 객체에서 토큰 추출
			String token = client.getHandshakeData().getSingleUrlParam("token");
			
			// auth 객체에서 토큰 추출 시도 (Socket.io 클라이언트가 auth로 보낼 경우)
			if (token == null || token.isEmpty()) {
				Object authObj = client.getHandshakeData().getHttpHeaders().get("Authorization");
				if (authObj != null) {
					String authHeader = authObj.toString();
					if (authHeader.startsWith("Bearer ")) {
						token = authHeader.substring(7);
					}
				}
			}
			
			if (token == null || token.isEmpty()) {
				log.warn("Client {} connected without token", client.getSessionId());
				client.disconnect();
				return;
			}

			// 토큰 검증
			if (!jwtTokenProvider.validateToken(token)) {
				log.warn("Client {} connected with invalid token", client.getSessionId());
				client.disconnect();
				return;
			}

			// 사용자 ID 추출
			Long userId = jwtTokenProvider.getUserIdFromToken(token);
			User user = userRepository.findByIdForAuth(userId)
				.orElseThrow(() -> {
					log.warn("User {} not found for client {}", userId, client.getSessionId());
					client.disconnect();
					return new ForbiddenException(ErrorCode.USER_NOT_FOUND);
				});

			// 사용자 상태 확인
			if (!user.isActive()) {
				log.warn("User {} is not active for client {}", userId, client.getSessionId());
				client.disconnect();
				return;
			}

			// 클라이언트와 사용자 ID 매핑 저장
			clientUserIdMap.put(client.getSessionId().toString(), userId);
			client.set("userId", userId);

			log.info("Client {} connected for user {}", client.getSessionId(), userId);
		} catch (Exception e) {
			log.error("Error during client connection", e);
			client.disconnect();
		}
	}

	/**
	 * 클라이언트 연결 해제 시 정리
	 */
	@OnDisconnect
	public void onDisconnect(SocketIOClient client) {
		Long userId = clientUserIdMap.remove(client.getSessionId().toString());
		if (userId != null) {
			// 대기열에서 제거
			try {
				if (matchQueueService.isInQueue(userId)) {
					matchService.cancelMatching(userId);
					log.info("User {} disconnected and removed from queue", userId);
				}
			} catch (Exception e) {
				log.error("Error removing user {} from queue on disconnect", userId, e);
			}
		}
		log.info("Client {} disconnected", client.getSessionId());
	}

	/**
	 * 매칭 시작 이벤트
	 */
	@OnEvent("match:start")
	public void onMatchStart(SocketIOClient client) {
		Long userId = getUserId(client);
		if (userId == null) {
			return;
		}

		try {
			// 진행 중인 통화 확인
			if (callSessionRepository.findInProgressByUserId(userId).isPresent()) {
				client.sendEvent("match:error", Map.of(
					"code", "ALREADY_IN_CALL",
					"message", "이미 통화 중입니다."
				));
				return;
			}

			// 매칭 시작
			var response = matchService.startMatching(userId);

			if (response.matched()) {
				// 즉시 매칭 성공
				client.sendEvent("match:success", Map.of(
					"sessionId", response.sessionId(),
					"webrtcChannelId", response.webrtcChannelId()
				));
				log.info("User {} matched immediately via socket", userId);
			} else {
				// 대기 중 - 비동기로 매칭 시도
				startAsyncMatching(userId, client);
			}
		} catch (Exception e) {
			log.error("Error during match start for user {}", userId, e);
			client.sendEvent("match:error", Map.of(
				"code", "MATCHING_FAILED",
				"message", "매칭에 실패했습니다."
			));
		}
	}

	/**
	 * 매칭 취소 이벤트
	 */
	@OnEvent("match:cancel")
	public void onMatchCancel(SocketIOClient client) {
		Long userId = getUserId(client);
		if (userId == null) {
			return;
		}

		try {
			matchService.cancelMatching(userId);
			client.sendEvent("match:cancelled", Map.of("success", true));
			log.info("User {} cancelled matching via socket", userId);
		} catch (Exception e) {
			log.error("Error during match cancel for user {}", userId, e);
			client.sendEvent("match:error", Map.of(
				"code", "CANCEL_FAILED",
				"message", "매칭 취소에 실패했습니다."
			));
		}
	}

	/**
	 * 비동기 매칭 시도
	 * - 대기 중인 사용자에게 주기적으로 매칭 시도
	 */
	@Async
	private void startAsyncMatching(Long userId, SocketIOClient client) {
		Thread matchingThread = new Thread(() -> {
			try {
				while (matchQueueService.isInQueue(userId) && client.isChannelOpen()) {
					Thread.sleep(2000); // 2초마다 시도

					Long matchedUserId = matchQueueService.findMatch(userId);
					if (matchedUserId != null) {
						// 매칭 성공
						User user1 = userRepository.findByIdForAuth(userId)
							.orElseThrow(() -> new RuntimeException("User not found"));
						User user2 = userRepository.findByIdForAuth(matchedUserId)
							.orElseThrow(() -> new RuntimeException("User not found"));

						String webrtcChannelId = "channel_" + java.util.UUID.randomUUID().toString().replace("-", "");
						CallSession session = matchService.createMatchedSession(user1, user2, webrtcChannelId);

						// 두 클라이언트 모두에게 알림
						client.sendEvent("match:success", Map.of(
							"sessionId", session.getId(),
							"webrtcChannelId", session.getWebrtcChannelId()
						));

						// 상대방 클라이언트 찾기
						clientUserIdMap.entrySet().stream()
							.filter(entry -> entry.getValue().equals(matchedUserId))
							.findFirst()
							.ifPresent(entry -> {
								try {
									java.util.UUID sessionUuid = java.util.UUID.fromString(entry.getKey());
									SocketIOClient matchedClient = socketIOServer.getClient(sessionUuid);
									if (matchedClient != null && matchedClient.isChannelOpen()) {
										matchedClient.sendEvent("match:success", Map.of(
											"sessionId", session.getId(),
											"webrtcChannelId", session.getWebrtcChannelId()
										));
										log.info("Notified matched user {} via socket", matchedUserId);
									}
								} catch (Exception e) {
									log.error("Error notifying matched user {}", matchedUserId, e);
								}
							});

						log.info("User {} matched with {} via async matching", userId, matchedUserId);
						return;
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.info("Matching thread interrupted for user {}", userId);
			} catch (Exception e) {
				log.error("Error during async matching for user {}", userId, e);
				if (client.isChannelOpen()) {
					client.sendEvent("match:error", Map.of(
						"code", "MATCHING_FAILED",
						"message", "매칭에 실패했습니다."
					));
				}
			}
		});
		matchingThread.setDaemon(true);
		matchingThread.start();
	}

	/**
	 * 클라이언트에서 사용자 ID 가져오기
	 */
	private Long getUserId(SocketIOClient client) {
		Object userIdObj = client.get("userId");
		if (userIdObj == null) {
			log.warn("Client {} has no userId", client.getSessionId());
			client.disconnect();
			return null;
		}
		return (Long) userIdObj;
	}
}
