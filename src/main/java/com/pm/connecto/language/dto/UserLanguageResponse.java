package com.pm.connecto.language.dto;

import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.language.domain.UserLanguage;

public record UserLanguageResponse(
	Long id,
	String languageCode,
	String languageName,
	LanguageType type,
	LanguageLevel level
) {
	public static UserLanguageResponse from(UserLanguage userLanguage) {
		return new UserLanguageResponse(
			userLanguage.getId(),
			userLanguage.getLanguage().getCode(),
			userLanguage.getLanguage().getName(),
			userLanguage.getType(),
			userLanguage.getLevel()
		);
	}
}
