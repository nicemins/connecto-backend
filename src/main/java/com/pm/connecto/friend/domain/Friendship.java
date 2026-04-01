package com.pm.connecto.friend.domain;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friendships",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_friendship", columnNames = {"user1_id", "user2_id"})
	},
	indexes = {
		@Index(name = "idx_friendship_user1", columnList = "user1_id"),
		@Index(name = "idx_friendship_user2", columnList = "user2_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

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

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	@Builder
	public Friendship(User user1, User user2) {
		this.user1 = user1;
		this.user2 = user2;
	}

	public boolean isMember(Long userId) {
		return user1.getId().equals(userId) || user2.getId().equals(userId);
	}

	public User getOtherUser(Long userId) {
		if (user1.getId().equals(userId)) return user2;
		if (user2.getId().equals(userId)) return user1;
		throw new IllegalArgumentException("User is not part of this friendship");
	}
}
