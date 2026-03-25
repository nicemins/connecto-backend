package com.pm.connecto.chat.dto;

import java.time.LocalDateTime;

import com.pm.connecto.chat.domain.ChatMessage;
import com.pm.connecto.chat.domain.MessageType;

public record ChatMessageResponse(
	Long id,
	Long senderId,
	String content,
	String imageUrl,
	String messageType,
	LocalDateTime createdAt
) {
	public static ChatMessageResponse from(ChatMessage msg) {
		return new ChatMessageResponse(
			msg.getId(),
			msg.getSender().getId(),
			msg.getContent(),
			msg.getImageUrl(),
			resolveType(msg.getMessageType()),
			msg.getCreatedAt()
		);
	}

	// LAZY 프록시 접근 방지용 — senderId를 트랜잭션 내에서 직접 전달
	public static ChatMessageResponse from(ChatMessage msg, Long senderId) {
		return new ChatMessageResponse(
			msg.getId(),
			senderId,
			msg.getContent(),
			msg.getImageUrl(),
			resolveType(msg.getMessageType()),
			msg.getCreatedAt()
		);
	}

	private static String resolveType(MessageType type) {
		return type != null ? type.name() : MessageType.TEXT.name();
	}
}
