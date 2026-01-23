package com.pm.connecto.user.dto;

public record UserCreateRequest(
	String email,
	String nickname,
	String password
) {
}
