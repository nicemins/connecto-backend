package com.pm.connecto.friend.dto;

public record FriendCheckResponse(
	boolean isFriend,
	Long friendshipId,
	boolean isBlocked
) {
	public static FriendCheckResponse of(boolean isFriend, Long friendshipId, boolean isBlocked) {
		return new FriendCheckResponse(isFriend, friendshipId, isBlocked);
	}
}
