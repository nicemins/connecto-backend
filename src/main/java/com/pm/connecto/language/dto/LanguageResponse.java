package com.pm.connecto.language.dto;

import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;

public record LanguageResponse(
	Long id,
	String languageCode,
	LanguageType type,
	LanguageLevel level
) {
	public static LanguageResponse from(Language language) {
		return new LanguageResponse(
			language.getId(),
			language.getLanguageCode(),
			language.getType(),
			language.getLevel()
		);
	}
}
