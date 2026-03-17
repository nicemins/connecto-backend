package com.pm.connecto.friend.dto;

import java.time.LocalDateTime;

import com.pm.connecto.friend.domain.Friendship;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.user.domain.User;

public record FriendResponse(
	Long friendshipId,
	Long userId,
	String nickname,
	String profileImageUrl,
	String bio,
	LocalDateTime friendSince
) {

	public static FriendResponse from(Friendship friendship, Long myUserId, Profile friendProfile) {
		User friend = friendship.getOtherUser(myUserId);
		return new FriendResponse(
			friendship.getId(),
			friend.getId(),
			friendProfile != null ? friendProfile.getNickname() : null,
			friendProfile != null ? friendProfile.getProfileImageUrl() : null,
			friendProfile != null ? friendProfile.getBio() : null,
			friendship.getCreatedAt()
		);
	}
}
