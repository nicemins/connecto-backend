package com.pm.connecto.chat.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.chat.service.ChatService;
import com.pm.connecto.chat.service.ChatService.SavedMessage;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.socket.SocketAuthUtil;
import com.pm.connecto.match.handler.MatchSocketHandler;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class ChatSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

	private final SocketIOServer socketIOServer;
	private final JwtTokenProvider jwtTokenProvider;
	private final ChatService chatService;
	private final MatchSocketHandler matchSocketHandler;

	public ChatSocketHandler(
		SocketIOServer socketIOServer,
		JwtTokenProvider jwtTokenProvider,
		ChatService chatService,
		MatchSocketHandler matchSocketHandler
	) {
		this.socketIOServer = socketIOServer;
		this.jwtTokenProvider = jwtTokenProvider;
		this.chatService = chatService;
		this.matchSocketHandler = matchSocketHandler;
	}

	@PostConstruct
	public void registerEvents() {
		socketIOServer.addEventListener("chat:send", Map.class, this::onChatSend);
		socketIOServer.addEventListener("chat:typing", Map.class, this::onChatTyping);
		log.info("ChatSocketHandler registered events: chat:send, chat:typing");
	}

	@SuppressWarnings("unchecked")
	public void onChatSend(SocketIOClient client, Map<String, Object> data, AckRequest ack) {
		Long senderId = extractUserId(client);
		if (senderId == null) {
			client.sendEvent("chat:error", Map.of("message", "인증이 필요합니다."));
			return;
		}

		try {
			Long roomId = toLong(data.get("roomId"));
			String content = (String) data.get("content");

			if (roomId == null || content == null || content.isBlank()) {
				client.sendEvent("chat:error", Map.of("message", "roomId와 content는 필수입니다."));
				return;
			}
			if (content.length() > 1000) {
				client.sendEvent("chat:error", Map.of("message", "메시지는 1000자를 초과할 수 없습니다."));
				return;
			}

			SavedMessage saved = chatService.saveMessage(roomId, senderId, content);
			Map<String, Object> payload = Map.of("roomId", roomId, "message", saved.messageResponse());

			// 발신자: chat:sent (ACK) + chat:receive (echo), 수신자: chat:receive
			matchSocketHandler.emitToUser(senderId, "chat:sent", payload);
			matchSocketHandler.emitToUser(senderId, "chat:receive", payload);
			matchSocketHandler.emitToUser(saved.otherUserId(), "chat:receive", payload);

		} catch (ForbiddenException | ResourceNotFoundException e) {
			// 비즈니스 예외 — 메시지 그대로 전달 (안전)
			log.warn("chat:send business error for user {}: {}", senderId, e.getMessage());
			client.sendEvent("chat:error", Map.of("message", e.getMessage()));
		} catch (Exception e) {
			// 예상치 못한 예외 — 내부 정보 노출 방지
			log.error("chat:send unexpected error for user {}", senderId, e);
			client.sendEvent("chat:error", Map.of("message", "메시지 전송에 실패했습니다."));
		}
	}

	@SuppressWarnings("unchecked")
	public void onChatTyping(SocketIOClient client, Map<String, Object> data, AckRequest ack) {
		Long senderId = extractUserId(client);
		if (senderId == null) {
			client.sendEvent("chat:error", Map.of("message", "인증이 필요합니다."));
			return;
		}

		try {
			Long roomId = toLong(data.get("roomId"));
			if (roomId == null) {
				client.sendEvent("chat:error", Map.of("message", "roomId는 필수입니다."));
				return;
			}

			Long otherUserId = chatService.getOtherUserId(roomId, senderId);
			matchSocketHandler.emitToUser(otherUserId, "chat:typing", Map.of("roomId", roomId));

		} catch (ForbiddenException | ResourceNotFoundException e) {
			log.warn("chat:typing business error for user {}: {}", senderId, e.getMessage());
			client.sendEvent("chat:error", Map.of("message", e.getMessage()));
		} catch (Exception e) {
			log.error("chat:typing unexpected error for user {}", senderId, e);
			client.sendEvent("chat:error", Map.of("message", "타이핑 이벤트 전송에 실패했습니다."));
		}
	}

	private Long extractUserId(SocketIOClient client) {
		return SocketAuthUtil.extractUserId(client, jwtTokenProvider);
	}

	private Long toLong(Object val) {
		if (val == null) return null;
		if (val instanceof Number n) return n.longValue();
		try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
	}
}
