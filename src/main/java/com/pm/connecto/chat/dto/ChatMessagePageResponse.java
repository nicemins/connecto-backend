package com.pm.connecto.chat.dto;

import java.util.List;

public record ChatMessagePageResponse(
	List<ChatMessageResponse> messages,
	boolean hasNext,
	int page,
	int size,
	Long partnerLastReadMessageId  // 상대방이 마지막으로 읽은 messageId — 읽음 표시 렌더링용
) {}
