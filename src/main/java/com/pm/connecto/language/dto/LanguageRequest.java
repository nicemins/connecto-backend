package com.pm.connecto.language.dto;

import java.util.List;

import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LanguageRequest(
	@NotEmpty(message = "언어 목록은 비어있을 수 없습니다.")
	@Size(max = 10, message = "언어는 최대 10개까지 등록할 수 있습니다.")
	List<@Valid LanguageItem> languages
) {

	public record LanguageItem(
		@NotBlank(message = "언어 코드는 필수입니다.")
		@Size(min = 2, max = 5, message = "언어 코드는 2자 이상 5자 이하여야 합니다.")
		@Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$", message = "올바른 언어 코드 형식이 아닙니다. (예: ko, en, ja, zh-CN)")
		String languageCode,

		@NotNull(message = "언어 타입은 필수입니다.")
		LanguageType type,

		@NotNull(message = "언어 레벨은 필수입니다.")
		LanguageLevel level
	) {
	}
}
