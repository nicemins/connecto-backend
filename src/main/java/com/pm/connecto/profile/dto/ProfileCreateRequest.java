package com.pm.connecto.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileCreateRequest(
	@NotBlank(message = "닉네임은 필수입니다.")
	@Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
	String nickname,

	@Size(max = 500, message = "프로필 이미지 URL은 500자 이하여야 합니다.")
	String profileImageUrl,

	@Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
	String bio
) {
}
