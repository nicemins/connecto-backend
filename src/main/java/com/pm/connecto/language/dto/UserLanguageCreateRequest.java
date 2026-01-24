package com.pm.connecto.language.dto;

import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;

public record UserLanguageCreateRequest(
	String languageCode,
	LanguageType type,
	LanguageLevel level
) {}
