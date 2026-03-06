package com.pm.connecto.call.dto;

public record FriendCallResponse(
	Long sessionId,
	String webrtcChannelId,
	Long friendId
) {}
