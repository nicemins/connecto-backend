package com.pm.connecto.friend.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friend_requests",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_friend_request", columnNames = {"sender_id", "receiver_id"})
	},
	indexes = {
		@Index(name = "idx_friend_req_sender", columnList = "sender_id"),
		@Index(name = "idx_friend_req_receiver", columnList = "receiver_id"),
		@Index(name = "idx_friend_req_status", columnList = "status")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_id", nullable = false)
	private User receiver;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FriendRequestStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
		if (status == null) {
			status = FriendRequestStatus.PENDING;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	@Builder
	public FriendRequest(User sender, User receiver) {
		this.sender = sender;
		this.receiver = receiver;
		this.status = FriendRequestStatus.PENDING;
	}

	public void accept() {
		this.status = FriendRequestStatus.ACCEPTED;
	}

	public void reject() {
		this.status = FriendRequestStatus.REJECTED;
	}

	public boolean isPending() {
		return status == FriendRequestStatus.PENDING;
	}
}
