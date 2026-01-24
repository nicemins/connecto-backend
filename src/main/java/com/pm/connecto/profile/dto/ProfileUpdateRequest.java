package com.pm.connecto.profile.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
	@Size(min = 2, max = 30, message = "닉네임은 2자 이상 30자 이하여야 합니다.")
	String nickname,

	@Size(max = 500, message = "프로필 이미지 URL은 500자 이하여야 합니다.")
	String profileImageUrl,

	@Size(max = 200, message = "자기소개는 200자 이하여야 합니다.")
	String bio
) {
}
