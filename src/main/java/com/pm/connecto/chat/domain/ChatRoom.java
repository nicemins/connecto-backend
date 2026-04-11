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

	// null = 나가지 않음, non-null = 나간 시각 (이후 메시지만 표시)
	@Column(name = "user1_left_at")
	private LocalDateTime user1LeftAt;

	@Column(name = "user2_left_at")
	private LocalDateTime user2LeftAt;

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

	/** 나간 상태 여부 */
	public boolean hasLeft(Long userId) {
		if (user1.getId().equals(userId)) return user1LeftAt != null;
		if (user2.getId().equals(userId)) return user2LeftAt != null;
		return false;
	}

	/** 나간 시각 조회 — 메시지 필터링에 사용 */
	public LocalDateTime getLeftAt(Long userId) {
		if (user1.getId().equals(userId)) return user1LeftAt;
		if (user2.getId().equals(userId)) return user2LeftAt;
		return null;
	}

	/** 나가기 — 나간 시각 기록 */
	public void leave(Long userId) {
		if (user1.getId().equals(userId)) this.user1LeftAt = LocalDateTime.now();
		else if (user2.getId().equals(userId)) this.user2LeftAt = LocalDateTime.now();
	}

	/** 재진입 — 새 메시지 수신 시 호출 */
	public void rejoin(Long userId) {
		if (user1.getId().equals(userId)) this.user1LeftAt = null;
		else if (user2.getId().equals(userId)) this.user2LeftAt = null;
	}
}
