package com.pm.connecto.chat.domain;

import java.time.LocalDateTime;

import com.pm.connecto.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages",
	indexes = {
		@Index(name = "idx_chat_message_room_created", columnList = "room_id, created_at DESC")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id", nullable = false)
	private ChatRoom room;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'TEXT'")
	private MessageType messageType = MessageType.TEXT;

	// TEXT: 필수, IMAGE: null
	@Column(length = 1000)
	private String content;

	// IMAGE 타입 전용 (TEXT: null)
	@Column(length = 1000)
	private String imageUrl;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public ChatMessage(ChatRoom room, User sender, String content) {
		this.room = room;
		this.sender = sender;
		this.content = content;
		this.messageType = MessageType.TEXT;
	}

	public ChatMessage(ChatRoom room, User sender, String imageUrl, MessageType messageType) {
		this.room = room;
		this.sender = sender;
		this.imageUrl = imageUrl;
		this.messageType = messageType;
	}
}
