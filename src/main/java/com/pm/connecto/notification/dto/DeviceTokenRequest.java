package com.pm.connecto.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
	@NotBlank @Size(max = 500) String token,
	@NotBlank @Pattern(regexp = "android|ios") String platform
) {}
