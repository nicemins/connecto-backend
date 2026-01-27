package com.pm.connecto.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.user.domain.User;

public record UserMeResponse(
	UserInfo user,
	ProfileInfo profile,
	List<LanguageInfo> languages
) {

	// ========== 중첩 DTO ==========

	public record UserInfo(
		Long id,
		String email,
		LocalDateTime createdAt
	) {
		public static UserInfo from(User user) {
			return new UserInfo(
				user.getId(),
				user.getEmail(),
				user.getCreatedAt()
			);
		}
	}

	public record ProfileInfo(
		Long id,
		String nickname,
		String profileImageUrl,
		String bio
	) {
		public static ProfileInfo from(Profile profile) {
			if (profile == null) {
				return null;
			}
			return new ProfileInfo(
				profile.getId(),
				profile.getNickname(),
				profile.getProfileImageUrl(),
				profile.getBio()
			);
		}
	}

	public record LanguageInfo(
		Long id,
		String languageCode,
		LanguageType type,
		LanguageLevel level
	) {
		public static LanguageInfo from(Language language) {
			return new LanguageInfo(
				language.getId(),
				language.getLanguageCode(),
				language.getType(),
				language.getLevel()
			);
		}
	}

	// ========== Builder 패턴 ==========

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private User user;
		private Profile profile;
		private List<Language> languages = List.of();

		public Builder user(User user) {
			this.user = user;
			return this;
		}

		public Builder profile(Profile profile) {
			this.profile = profile;
			return this;
		}

		public Builder languages(List<Language> languages) {
			this.languages = languages != null ? languages : List.of();
			return this;
		}

		public UserMeResponse build() {
			return new UserMeResponse(
				UserInfo.from(user),
				ProfileInfo.from(profile),
				languages.stream().map(LanguageInfo::from).toList()
			);
		}
	}
}
