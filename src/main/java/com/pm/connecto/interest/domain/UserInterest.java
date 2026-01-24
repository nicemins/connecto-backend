package com.pm.connecto.interest.domain;

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
@Table(name = "user_interests",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_interest", columnNames = {"user_id", "interest_id"})
	},
	indexes = {
		@Index(name = "idx_user_interest_user_id", columnList = "user_id"),
		@Index(name = "idx_user_interest_interest_id", columnList = "interest_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "interest_id", nullable = false)
	private Interest interest;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public UserInterest(User user, Interest interest) {
		this.user = user;
		this.interest = interest;
	}
}
