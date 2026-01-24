package com.pm.connecto.language.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "languages",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_language_type", columnNames = {"user_id", "language_code", "type"})
	},
	indexes = {
		@Index(name = "idx_language_user_id", columnList = "user_id"),
		@Index(name = "idx_language_code", columnList = "language_code"),
		@Index(name = "idx_language_type", columnList = "type"),
		@Index(name = "idx_language_matching", columnList = "language_code, type, level")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Language {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "language_code", nullable = false, length = 10)
	private String languageCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LanguageType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LanguageLevel level;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	@Builder
	public Language(User user, String languageCode, LanguageType type, LanguageLevel level) {
		this.user = user;
		this.languageCode = languageCode;
		this.type = type;
		this.level = level;
	}

	public void updateLevel(LanguageLevel level) {
		this.level = level;
	}
}
