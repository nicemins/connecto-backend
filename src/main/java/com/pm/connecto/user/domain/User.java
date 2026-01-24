package com.pm.connecto.user.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = {
	@Index(name = "idx_user_email", columnList = "email"),
	@Index(name = "idx_user_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column(nullable = false)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
		if (status == null) {
			status = UserStatus.ACTIVE;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public User(String email, String password) {
		this.email = email;
		this.password = password;
		this.status = UserStatus.ACTIVE;
	}

	public void updatePassword(String encodedPassword) {
		if (encodedPassword != null && !encodedPassword.isBlank()) {
			this.password = encodedPassword;
		}
	}

	public void block() {
		this.status = UserStatus.BLOCKED;
	}

	public void unblock() {
		if (this.status == UserStatus.BLOCKED) {
			this.status = UserStatus.ACTIVE;
		}
	}

	public void delete() {
		this.status = UserStatus.DELETED;
		this.deletedAt = LocalDateTime.now();
	}

	public boolean isActive() {
		return this.status == UserStatus.ACTIVE;
	}

	public boolean isBlocked() {
		return this.status == UserStatus.BLOCKED;
	}

	public boolean isDeleted() {
		return this.status == UserStatus.DELETED || this.deletedAt != null;
	}
}
