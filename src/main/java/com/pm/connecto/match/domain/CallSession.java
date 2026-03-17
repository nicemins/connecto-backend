package com.pm.connecto.match.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "call_sessions", indexes = {
	@Index(name = "idx_call_session_user1", columnList = "user1_id"),
	@Index(name = "idx_call_session_user2", columnList = "user2_id"),
	@Index(name = "idx_call_session_status", columnList = "status"),
	@Index(name = "idx_call_session_created_at", columnList = "created_at"),
	@Index(name = "idx_call_session_started_at", columnList = "started_at"),
	@Index(name = "idx_call_session_status_started", columnList = "status,started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user1_id", nullable = false)
	private User user1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user2_id", nullable = false)
	private User user2;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CallSessionStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	@Column(length = 100)
	private String webrtcChannelId;

	@Column(nullable = false)
	private Boolean user1WantAgain = false;

	@Column(nullable = false)
	private Boolean user2WantAgain = false;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
		if (status == null) {
			status = CallSessionStatus.WAITING;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public CallSession(User user1, User user2) {
		this.user1 = user1;
		this.user2 = user2;
		this.status = CallSessionStatus.WAITING;
	}

	public void start(String webrtcChannelId) {
		this.status = CallSessionStatus.IN_PROGRESS;
		this.startedAt = LocalDateTime.now();
		this.webrtcChannelId = webrtcChannelId;
	}

	public void end() {
		this.status = CallSessionStatus.ENDED;
		this.endedAt = LocalDateTime.now();
	}

	public void setUser1WantAgain(boolean wantAgain) {
		this.user1WantAgain = wantAgain;
	}

	public void setUser2WantAgain(boolean wantAgain) {
		this.user2WantAgain = wantAgain;
	}

	public boolean bothWantAgain() {
		return user1WantAgain && user2WantAgain;
	}

	public boolean isEnded() {
		return status == CallSessionStatus.ENDED;
	}

	public boolean isInProgress() {
		return status == CallSessionStatus.IN_PROGRESS;
	}

	public User getOtherUser(Long userId) {
		if (user1.getId().equals(userId)) {
			return user2;
		} else if (user2.getId().equals(userId)) {
			return user1;
		}
		throw new IllegalArgumentException("User is not part of this session");
	}
}
