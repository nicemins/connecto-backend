package com.pm.connecto.chat.domain;

import java.time.LocalDateTime;

import com.pm.connecto.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "chat_room_members",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_chat_room_member",
		columnNames = {"chat_room_id", "user_id"}
	),
	indexes = {
		@Index(name = "idx_chat_room_member_room_user", columnList = "chat_room_id, user_id"),
		@Index(name = "idx_chat_room_member_user", columnList = "user_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom room;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column
	private Long lastReadMessageId;  // null = 한 번도 읽지 않음

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		updatedAt = LocalDateTime.now();
	}

	public ChatRoomMember(ChatRoom room, User user) {
		this.room = room;
		this.user = user;
	}

	/**
	 * lastReadMessageId 업데이트 — MAX 유지 (이전 값으로 되돌리기 불가)
	 */
	public void updateLastRead(Long messageId) {
		if (messageId != null && (this.lastReadMessageId == null || messageId > this.lastReadMessageId)) {
			this.lastReadMessageId = messageId;
			this.updatedAt = LocalDateTime.now();
		}
	}
}
