package com.pm.connecto.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatRoomCreateRequest(
	@NotNull(message = "친구 ID는 필수입니다.") Long friendId
) {}
