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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "언어", description = "사용자 언어 설정 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/users/me/languages")
public class LanguageController {

	private final LanguageService languageService;
	private final UserContext userContext;

	public LanguageController(LanguageService languageService, UserContext userContext) {
		this.languageService = languageService;
		this.userContext = userContext;
	}

	@Operation(summary = "언어 추가", description = "새로운 언어를 추가합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 언어")
	})
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

	@Operation(summary = "언어 목록 조회", description = "등록된 언어 목록을 조회합니다. 타입별 필터링 가능.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping
	public ApiResponse<List<LanguageResponse>> getLanguages(
		@Parameter(description = "언어 타입 필터 (NATIVE, LEARNING)")
		@RequestParam(required = false) LanguageType type
	) {
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

	@Operation(summary = "언어 전체 교체", description = "기존 언어를 모두 삭제하고 새로운 목록으로 교체합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "교체 성공")
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

	@Operation(summary = "언어 삭제", description = "특정 언어를 삭제합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "언어 없음")
	})
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteLanguage(
		@Parameter(description = "언어 ID", example = "1")
		@PathVariable Long id
	) {
		languageService.deleteLanguage(userContext.getUserId(), id);
	}
}
