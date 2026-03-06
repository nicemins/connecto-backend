package com.pm.connecto.friend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FriendRequestCreateRequest(
	@NotNull(message = "수신자 ID는 필수입니다.")
	@Positive(message = "수신자 ID는 양수여야 합니다.")
	Long receiverId
) {}
