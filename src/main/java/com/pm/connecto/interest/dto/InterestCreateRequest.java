package com.pm.connecto.interest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterestCreateRequest(
	@NotBlank(message = "관심사는 필수입니다.")
	@Size(max = 50, message = "관심사는 50자 이하여야 합니다.")
	String tag
) {}
