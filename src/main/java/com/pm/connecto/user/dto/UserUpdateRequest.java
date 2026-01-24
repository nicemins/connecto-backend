package com.pm.connecto.user.dto;

public record UserUpdateRequest(
	String nickname,
	String password
) {
}
