package com.pm.connecto.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.report.dto.ReportCreateRequest;
import com.pm.connecto.report.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "신고", description = "사용자 신고 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/reports")
public class ReportController {

	private final ReportService reportService;
	private final UserContext userContext;

	public ReportController(ReportService reportService, UserContext userContext) {
		this.reportService = reportService;
		this.userContext = userContext;
	}

	@Operation(summary = "사용자 신고", description = "통화 세션에서 상대방을 신고합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신고 완료"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "자기 자신 신고"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 신고")
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<Void> report(@Valid @RequestBody ReportCreateRequest request) {
		reportService.report(
			userContext.getUserId(),
			request.reportedUserId(),
			request.sessionId(),
			request.reason()
		);
		return ApiResponse.success(null);
	}
}
