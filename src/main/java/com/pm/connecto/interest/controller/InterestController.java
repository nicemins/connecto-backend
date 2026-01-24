package com.pm.connecto.interest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.dto.InterestResponse;
import com.pm.connecto.interest.dto.UserInterestRequest;
import com.pm.connecto.interest.service.InterestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping
public class InterestController {

	private final InterestService interestService;
	private final UserContext userContext;

	public InterestController(InterestService interestService, UserContext userContext) {
		this.interestService = interestService;
		this.userContext = userContext;
	}

	// ========== 전체 관심사 목록 (마스터 데이터) ==========

	@GetMapping("/interests")
	public ApiResponse<List<InterestResponse>> getAllInterests() {
		List<Interest> interests = interestService.getAllInterests();
		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	// ========== 사용자 관심사 ==========

	@PostMapping("/users/me/interests")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<List<InterestResponse>> addUserInterests(@Valid @RequestBody UserInterestRequest userInterestRequest) {
		List<Interest> interests = interestService.addUserInterests(
			userContext.getUserId(),
			userInterestRequest.interestNames()
		);

		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@GetMapping("/users/me/interests")
	public ApiResponse<List<InterestResponse>> getUserInterests() {
		List<Interest> interests = interestService.getUserInterests(userContext.getUserId());
		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	/**
	 * 사용자 관심사 전체 교체
	 * - 기존 관심사 관계 전부 삭제 후 새로 연결
	 * - Interest 테이블에 없으면 자동 생성
	 * - 트랜잭션으로 원자성 보장
	 */
	@PutMapping("/users/me/interests")
	public ApiResponse<List<InterestResponse>> replaceUserInterests(@Valid @RequestBody UserInterestRequest userInterestRequest) {
		List<Interest> interests = interestService.replaceUserInterests(
			userContext.getUserId(),
			userInterestRequest.interestNames()
		);

		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@DeleteMapping("/users/me/interests/{interestId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeUserInterest(@PathVariable Long interestId) {
		interestService.removeUserInterestById(userContext.getUserId(), interestId);
	}
}
