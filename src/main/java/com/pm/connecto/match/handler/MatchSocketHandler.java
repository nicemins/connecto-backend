package com.pm.connecto.match.handler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

	private static final long MAX_WAIT_MS = 120_000L; // 최대 2분 대기

	// 클라이언트별 사용자 ID 매핑
	private final Map<String, Long> clientUserIdMap = new ConcurrentHashMap<>();

	// 채널별 소켓 클라이언트 추적 (WebRTC 시그널 릴레이용)
	private final Map<String, Set<SocketIOClient>> channelRoomMap = new ConcurrentHashMap<>();

	// 매칭 스레드 풀 (최대 100개로 제한)
	private final ExecutorService matchingExecutor = Executors.newFixedThreadPool(100);

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
		matchingExecutor.shutdown();
		socketIOServer.stop();
		log.info("Socket.io server stopped");
	}

	/**
	 * 클라이언트 연결 시 인증 처리
	 */
	@OnConnect
	public void onConnect(SocketIOClient client) {
		try {
			// 디버그: 실제로 들어오는 값 확인
			String authHeader = client.getHandshakeData().getHttpHeaders().get("Authorization");
			String tokenParam = client.getHandshakeData().getSingleUrlParam("token");
			log.debug("Socket connect - Authorization header: {}, token param present: {}",
				authHeader != null ? "present" : "null",
				tokenParam != null ? "present" : "null");

			// 방법 1: HTTP Authorization 헤더 (extraHeaders로 전송 시)
			String token = null;
			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				token = authHeader.substring(7);
			}

			// 방법 2: URL query param fallback (?token=...)
			if (token == null && tokenParam != null && !tokenParam.isEmpty()) {
				token = tokenParam;
			}

			if (token == null || token.isEmpty()) {
				log.warn("Client {} connected without token (no Authorization header or token param)", client.getSessionId());
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
		// channelRoomMap에서 해당 클라이언트 제거
		channelRoomMap.values().forEach(clients -> clients.remove(client));
		channelRoomMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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

			// 이미 대기열에 있으면 (REST API로 먼저 진입한 경우) 비동기 매칭만 시작
			if (matchQueueService.isInQueue(userId)) {
				log.info("User {} already in queue (via REST), starting async matching via socket", userId);
				startAsyncMatching(userId, client);
				return;
			}

			// 매칭 시작
			var response = matchService.startMatching(userId);

			if (response.matched()) {
				// 즉시 매칭 성공 — 현재 클라이언트가 Offerer
				client.sendEvent("match:success", Map.of(
					"sessionId", response.sessionId(),
					"webrtcChannelId", response.webrtcChannelId(),
					"isOfferer", true
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
	private void startAsyncMatching(Long userId, SocketIOClient client) {
		matchingExecutor.submit(() -> {
			final long startTime = System.currentTimeMillis();
			try {
				while (matchQueueService.isInQueue(userId) && client.isChannelOpen()) {
					if (System.currentTimeMillis() - startTime > MAX_WAIT_MS) {
						try { matchService.cancelMatching(userId); } catch (Exception ignored) {}
						if (client.isChannelOpen()) {
							client.sendEvent("match:error", Map.of(
								"code", "MATCHING_TIMEOUT",
								"message", "매칭 시간이 초과되었습니다."
							));
						}
						return;
					}

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

						// 현재 클라이언트(user1) → Offerer
						client.sendEvent("match:success", Map.of(
							"sessionId", session.getId(),
							"webrtcChannelId", session.getWebrtcChannelId(),
							"isOfferer", true
						));

						// 상대 클라이언트(user2) → Answerer
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
											"webrtcChannelId", session.getWebrtcChannelId(),
											"isOfferer", false
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
				log.info("Matching task interrupted for user {}", userId);
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
	}

	/**
	 * WebRTC 채널 입장
	 */
	@OnEvent("webrtc:join")
	public void onWebrtcJoin(SocketIOClient client, Map<String, Object> data) {
		Long userId = getUserId(client);
		if (userId == null) return;

		String channelId = (String) data.get("channelId");
		if (channelId == null) return;

		// 사용자가 해당 채널의 세션 참여자인지 검증
		if (callSessionRepository.findByWebrtcChannelIdAndUserId(channelId, userId).isEmpty()) {
			log.warn("User {} is not authorized for WebRTC channel {}", userId, channelId);
			client.sendEvent("webrtc:error", Map.of("message", "채널 접근 권한이 없습니다."));
			return;
		}

		channelRoomMap.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(client);
		log.info("User {} joined WebRTC channel {}", userId, channelId);
	}

	/**
	 * WebRTC Offer SDP 릴레이
	 */
	@OnEvent("webrtc:offer")
	public void onWebrtcOffer(SocketIOClient client, Map<String, Object> data) {
		relayToPeer(client, "webrtc:offer", data);
	}

	/**
	 * WebRTC Answer SDP 릴레이
	 */
	@OnEvent("webrtc:answer")
	public void onWebrtcAnswer(SocketIOClient client, Map<String, Object> data) {
		relayToPeer(client, "webrtc:answer", data);
	}

	/**
	 * WebRTC ICE Candidate 릴레이
	 */
	@OnEvent("webrtc:ice")
	public void onWebrtcIce(SocketIOClient client, Map<String, Object> data) {
		relayToPeer(client, "webrtc:ice", data);
	}

	/**
	 * 같은 채널의 상대방에게 이벤트 릴레이
	 */
	private void relayToPeer(SocketIOClient client, String eventName, Map<String, Object> data) {
		Long userId = getUserId(client);
		if (userId == null) return;

		String channelId = (String) data.get("channelId");
		if (channelId == null) return;

		Set<SocketIOClient> peers = channelRoomMap.get(channelId);
		if (peers == null) return;

		peers.stream()
			.filter(peer -> !peer.getSessionId().equals(client.getSessionId()))
			.filter(SocketIOClient::isChannelOpen)
			.findFirst()
			.ifPresent(peer -> peer.sendEvent(eventName, data));
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
