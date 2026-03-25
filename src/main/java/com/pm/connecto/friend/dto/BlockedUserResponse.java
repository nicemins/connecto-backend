package com.pm.connecto.friend.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(
	Long blockedUserId,
	String nickname,
	String profileImageUrl,
	LocalDateTime blockedAt
) {}
