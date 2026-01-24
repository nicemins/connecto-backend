package com.pm.connecto.user.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
	@Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
	String password
) {
}
