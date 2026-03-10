package com.pm.connecto.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceTokenRequest(
	@NotBlank String token,
	@NotBlank @Pattern(regexp = "android|ios") String platform
) {}
