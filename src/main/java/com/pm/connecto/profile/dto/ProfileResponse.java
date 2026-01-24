package com.pm.connecto.profile.dto;

import com.pm.connecto.profile.domain.Profile;

public record ProfileResponse(
	Long id,
	Long userId,
	String nickname,
	String profileImageUrl,
	String bio
) {
	public static ProfileResponse from(Profile profile) {
		return new ProfileResponse(
			profile.getId(),
			profile.getUser().getId(),
			profile.getNickname(),
			profile.getProfileImageUrl(),
			profile.getBio()
		);
	}
}
