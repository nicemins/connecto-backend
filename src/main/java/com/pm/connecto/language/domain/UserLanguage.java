package com.pm.connecto.language.domain;

import com.pm.connecto.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_languages", uniqueConstraints = {
	@UniqueConstraint(name = "uk_user_language_type", columnNames = {"user_id", "language_id", "type"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLanguage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "language_id", nullable = false)
	private Language language;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LanguageType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LanguageLevel level;

	public UserLanguage(User user, Language language, LanguageType type, LanguageLevel level) {
		this.user = user;
		this.language = language;
		this.type = type;
		this.level = level;
	}
}
