package com.pm.connecto.notification.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "device_tokens", indexes = {
	@Index(name = "idx_device_token_user_id", columnList = "user_id"),
	@Index(name = "idx_device_token_token", columnList = "token", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, unique = true, length = 500)
	private String token;

	@Column(nullable = false, length = 20)
	private String platform;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public DeviceToken(User user, String token, String platform) {
		this.user = user;
		this.token = token;
		this.platform = platform;
	}
}
