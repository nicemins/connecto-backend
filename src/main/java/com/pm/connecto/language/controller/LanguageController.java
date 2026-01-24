package com.pm.connecto.language.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.language.dto.LanguageCreateRequest;
import com.pm.connecto.language.dto.LanguageRequest;
import com.pm.connecto.language.dto.LanguageResponse;
import com.pm.connecto.language.service.LanguageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me/languages")
public class LanguageController {

	private final LanguageService languageService;
	private final UserContext userContext;

	public LanguageController(LanguageService languageService, UserContext userContext) {
		this.languageService = languageService;
		this.userContext = userContext;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<LanguageResponse> addLanguage(@Valid @RequestBody LanguageCreateRequest createRequest) {
		Language language = languageService.addLanguage(
			userContext.getUserId(),
			createRequest.languageCode(),
			createRequest.type(),
			createRequest.level()
		);
		return ApiResponse.success(LanguageResponse.from(language));
	}

	@GetMapping
	public ApiResponse<List<LanguageResponse>> getLanguages(@RequestParam(required = false) LanguageType type) {
		Long userId = userContext.getUserId();

		List<Language> languages;
		if (type != null) {
			languages = languageService.getLanguagesByType(userId, type);
		} else {
			languages = languageService.getLanguages(userId);
		}

		return ApiResponse.success(
			languages.stream()
				.map(LanguageResponse::from)
				.toList()
		);
	}

	/**
	 * 사용자 언어 전체 교체
	 * - 기존 언어 전부 삭제 후 새로 저장
	 * - 트랜잭션으로 원자성 보장
	 */
	@PutMapping
	public ApiResponse<List<LanguageResponse>> replaceLanguages(@Valid @RequestBody LanguageRequest languageRequest) {
		List<Language> languages = languageService.replaceLanguages(
			userContext.getUserId(),
			languageRequest.languages()
		);

		return ApiResponse.success(
			languages.stream()
				.map(LanguageResponse::from)
				.toList()
		);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteLanguage(@PathVariable Long id) {
		languageService.deleteLanguage(userContext.getUserId(), id);
	}
}
