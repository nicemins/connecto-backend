package com.pm.connecto.user.dto;

public record LoginRequest(
	String email,
	String password
) {
}
