package com.pm.connecto.friend.dto;

import java.time.LocalDateTime;

import com.pm.connecto.friend.domain.FriendRequest;
import com.pm.connecto.friend.domain.FriendRequestStatus;
import com.pm.connecto.profile.domain.Profile;

public record FriendRequestResponse(
	Long id,
	Long senderId,
	String senderNickname,
	String senderProfileImageUrl,
	Long receiverId,
	String receiverNickname,
	FriendRequestStatus status,
	LocalDateTime createdAt
) {

	public static FriendRequestResponse from(FriendRequest req, Profile senderProfile, Profile receiverProfile) {
		return new FriendRequestResponse(
			req.getId(),
			req.getSender().getId(),
			senderProfile != null ? senderProfile.getNickname() : null,
			senderProfile != null ? senderProfile.getProfileImageUrl() : null,
			req.getReceiver().getId(),
			receiverProfile != null ? receiverProfile.getNickname() : null,
			req.getStatus(),
			req.getCreatedAt()
		);
	}
}
