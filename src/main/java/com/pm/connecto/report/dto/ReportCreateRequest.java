package com.pm.connecto.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
	@NotNull Long sessionId,
	@NotNull Long reportedUserId,
	@Size(max = 500) String reason
) {}
