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
@Table(name = "chat_rooms",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_chat_room", columnNames = {"user1_id", "user2_id"})
	},
	indexes = {
		@Index(name = "idx_chat_room_user1", columnList = "user1_id"),
		@Index(name = "idx_chat_room_user2", columnList = "user2_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user1_id", nullable = false)
	private User user1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user2_id", nullable = false)
	private User user2;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@Column(nullable = false)
	private boolean user1Left = false;

	@Column(nullable = false)
	private boolean user2Left = false;

	@PrePersist
	protected void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	// user1.id < user2.id 순서 정규화 — uk_chat_room 방향성 문제 방지
	public ChatRoom(User u1, User u2) {
		if (u1.getId() < u2.getId()) {
			this.user1 = u1;
			this.user2 = u2;
		} else {
			this.user1 = u2;
			this.user2 = u1;
		}
	}

	public boolean isMember(Long userId) {
		return user1.getId().equals(userId) || user2.getId().equals(userId);
	}

	public User getOtherUser(Long userId) {
		if (user1.getId().equals(userId)) return user2;
		if (user2.getId().equals(userId)) return user1;
		throw new IllegalArgumentException("User is not a member of this chat room");
	}

	public void updateTimestamp(LocalDateTime time) {
		this.updatedAt = time;
	}

	public boolean hasLeft(Long userId) {
		if (user1.getId().equals(userId)) return user1Left;
		if (user2.getId().equals(userId)) return user2Left;
		return false;
	}

	public void leave(Long userId) {
		if (user1.getId().equals(userId)) this.user1Left = true;
		else if (user2.getId().equals(userId)) this.user2Left = true;
	}
}
