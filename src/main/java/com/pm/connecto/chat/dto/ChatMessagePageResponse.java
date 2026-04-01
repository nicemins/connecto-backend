package com.pm.connecto.chat.dto;

import java.util.List;

public record ChatMessagePageResponse(
	List<ChatMessageResponse> messages,
	boolean hasNext,
	int page,
	int size
) {}
