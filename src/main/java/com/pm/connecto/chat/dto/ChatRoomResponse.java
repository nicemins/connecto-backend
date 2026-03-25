package com.pm.connecto.chat.dto;

import java.time.LocalDateTime;

public record ChatRoomResponse(
	Long roomId,
	Long friendId,
	String friendNickname,
	String friendProfileImageUrl,
	String lastMessage,
	LocalDateTime updatedAt
) {}
