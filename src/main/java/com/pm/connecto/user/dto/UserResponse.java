package com.pm.connecto.user.dto;

import com.pm.connecto.user.domain.User;

public record UserResponse(
	Long id,
	String email
) {
	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getEmail()
		);
	}
}
