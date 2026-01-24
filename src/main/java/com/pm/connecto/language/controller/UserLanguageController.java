package com.pm.connecto.language.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.language.domain.UserLanguage;
import com.pm.connecto.language.dto.UserLanguageCreateRequest;
import com.pm.connecto.language.dto.UserLanguageResponse;
import com.pm.connecto.language.service.UserLanguageService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/users/me/languages")
public class UserLanguageController {

	private final UserLanguageService userLanguageService;

	public UserLanguageController(UserLanguageService userLanguageService) {
		this.userLanguageService = userLanguageService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserLanguageResponse addLanguage(
		HttpServletRequest request,
		@RequestBody UserLanguageCreateRequest createRequest
	) {
		Long userId = (Long) request.getAttribute("userId");
		if (userId == null) {
			throw new IllegalArgumentException("로그인이 필요합니다.");
		}

		UserLanguage userLanguage = userLanguageService.addUserLanguage(
			userId,
			createRequest.languageCode(),
			createRequest.type(),
			createRequest.level()
		);

		return UserLanguageResponse.from(userLanguage);
	}

	@GetMapping
	public List<UserLanguageResponse> getLanguages(HttpServletRequest request) {
		Long userId = (Long) request.getAttribute("userId");
		if (userId == null) {
			throw new IllegalArgumentException("로그인이 필요합니다.");
		}

		return userLanguageService.getUserLanguages(userId).stream()
			.map(UserLanguageResponse::from)
			.toList();
	}

	@DeleteMapping("/{id}")
	public Map<String, String> deleteLanguage(
		HttpServletRequest request,
		@PathVariable Long id
	) {
		Long userId = (Long) request.getAttribute("userId");
		if (userId == null) {
			throw new IllegalArgumentException("로그인이 필요합니다.");
		}

		userLanguageService.deleteUserLanguage(userId, id);
		return Map.of("message", "언어 설정이 삭제되었습니다.");
	}
}
