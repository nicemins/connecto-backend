package com.pm.connecto.user.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
	@NotBlank String provider,
	@NotBlank String token
) {}
