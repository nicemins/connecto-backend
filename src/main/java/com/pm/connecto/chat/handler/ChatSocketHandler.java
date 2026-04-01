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
import com.pm.connecto.chat.service.ChatService.ReadResult;
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
		socketIOServer.addEventListener("chat:join", Map.class, this::onChatJoin);
		socketIOServer.addEventListener("chat:send", Map.class, this::onChatSend);
		socketIOServer.addEventListener("chat:typing", Map.class, this::onChatTyping);
		socketIOServer.addEventListener("chat:leave", Map.class, this::onChatLeave);
		socketIOServer.addEventListener("chat:read", Map.class, this::onChatRead);
		log.info("ChatSocketHandler registered events: chat:join, chat:send, chat:typing, chat:leave, chat:read");
	}

	@SuppressWarnings("unchecked")
	public void onChatJoin(SocketIOClient client, Map<String, Object> data, AckRequest ack) {
		Long userId = extractUserId(client);
		if (userId == null) {
			client.sendEvent("chat:error", Map.of("message", "인증이 필요합니다."));
			return;
		}
		Long roomId = toLong(data.get("roomId"));
		if (roomId == null) {
			client.sendEvent("chat:error", Map.of("message", "roomId는 필수입니다."));
			return;
		}
		try {
			client.joinRoom("chat:" + roomId);
			// chat:join 시 자동 읽음 처리 → 상대방에게 chat:read emit
			ReadResult result = chatService.markAsReadLatest(roomId, userId);
			if (result != null && result.lastReadMessageId() != null) {
				matchSocketHandler.emitToUser(result.otherUserId(), "chat:read", Map.of(
					"roomId", roomId,
					"readerId", userId,
					"lastReadMessageId", result.lastReadMessageId()
				));
			}
			log.debug("User {} joined chat room {}", userId, roomId);
		} catch (ForbiddenException | ResourceNotFoundException e) {
			log.warn("chat:join unauthorized: user {} room {}", userId, roomId);
			client.sendEvent("chat:error", Map.of("message", e.getMessage()));
		}
	}

	@SuppressWarnings("unchecked")
	public void onChatRead(SocketIOClient client, Map<String, Object> data, AckRequest ack) {
		Long userId = extractUserId(client);
		if (userId == null) {
			client.sendEvent("chat:error", Map.of("message", "인증이 필요합니다."));
			return;
		}
		Long roomId = toLong(data.get("roomId"));
		if (roomId == null) {
			client.sendEvent("chat:error", Map.of("message", "roomId는 필수입니다."));
			return;
		}
		try {
			ReadResult result = chatService.markAsReadLatest(roomId, userId);
			if (result != null && result.lastReadMessageId() != null) {
				matchSocketHandler.emitToUser(result.otherUserId(), "chat:read", Map.of(
					"roomId", roomId,
					"readerId", userId,
					"lastReadMessageId", result.lastReadMessageId()
				));
			}
		} catch (ForbiddenException | ResourceNotFoundException e) {
			log.warn("chat:read business error for user {}: {}", userId, e.getMessage());
			client.sendEvent("chat:error", Map.of("message", e.getMessage()));
		} catch (Exception e) {
			log.error("chat:read unexpected error for user {}", userId, e);
			client.sendEvent("chat:error", Map.of("message", "읽음 처리에 실패했습니다."));
		}
	}

	@SuppressWarnings("unchecked")
	public void onChatLeave(SocketIOClient client, Map<String, Object> data, AckRequest ack) {
		Long userId = extractUserId(client);
		Long roomId = toLong(data.get("roomId"));
		if (roomId != null) {
			client.leaveRoom("chat:" + roomId);
			log.debug("User {} left chat room {}", userId, roomId);
		}
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

			// chat:receive — 룸 전체 브로드쾐스트 (발신자 포함, io.in(room) 방식)
			socketIOServer.getRoomOperations("chat:" + roomId).sendEvent("chat:receive", payload);

		} catch (ForbiddenException | ResourceNotFoundException e) {
			log.warn("chat:send business error for user {}: {}", senderId, e.getMessage());
			client.sendEvent("chat:error", Map.of("message", e.getMessage()));
		} catch (Exception e) {
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
