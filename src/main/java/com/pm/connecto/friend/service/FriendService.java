package com.pm.connecto.friend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.friend.domain.FriendRequest;
import com.pm.connecto.friend.domain.FriendRequestStatus;
import com.pm.connecto.friend.domain.Friendship;
import com.pm.connecto.friend.dto.FriendRequestResponse;
import com.pm.connecto.friend.dto.FriendResponse;
import com.pm.connecto.friend.repository.FriendRequestRepository;
import com.pm.connecto.friend.repository.FriendshipRepository;
import com.pm.connecto.notification.service.FcmService;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class FriendService {

	private static final Logger log = LoggerFactory.getLogger(FriendService.class);

	private final FriendRequestRepository friendRequestRepository;
	private final FriendshipRepository friendshipRepository;
	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final FcmService fcmService;

	public FriendService(
		FriendRequestRepository friendRequestRepository,
		FriendshipRepository friendshipRepository,
		UserRepository userRepository,
		ProfileRepository profileRepository,
		FcmService fcmService
	) {
		this.friendRequestRepository = friendRequestRepository;
		this.friendshipRepository = friendshipRepository;
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.fcmService = fcmService;
	}

	/**
	 * 친구 요청 전송
	 */
	@Transactional
	public FriendRequestResponse sendFriendRequest(Long senderId, Long receiverId) {
		if (senderId.equals(receiverId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		User sender = userRepository.findActiveById(senderId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
		User receiver = userRepository.findActiveById(receiverId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (friendRequestRepository.existsActiveRequestBetween(senderId, receiverId)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_FRIEND_REQUEST);
		}

		FriendRequest request = FriendRequest.builder()
			.sender(sender)
			.receiver(receiver)
			.build();
		friendRequestRepository.save(request);

		log.info("Friend request sent: {} → {}", senderId, receiverId);

		Profile senderProfile = profileRepository.findByUserId(senderId).orElse(null);
		Profile receiverProfile = profileRepository.findByUserId(receiverId).orElse(null);

		String senderNickname = senderProfile != null ? senderProfile.getNickname() : "누군가";
		fcmService.sendToUserAsync(receiverId, "친구 요청", senderNickname + "님이 친구 요청을 보냈어요");

		return FriendRequestResponse.from(request, senderProfile, receiverProfile);
	}

	/**
	 * 친구 요청 수락
	 */
	@Transactional
	public FriendRequestResponse acceptFriendRequest(Long receiverId, Long requestId) {
		FriendRequest request = friendRequestRepository.findByIdAndReceiverId(requestId, receiverId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

		if (!request.isPending()) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		if (friendshipRepository.existsBetween(request.getSender().getId(), receiverId)) {
			throw new DuplicateResourceException(ErrorCode.ALREADY_FRIENDS);
		}

		request.accept();

		Friendship friendship = Friendship.builder()
			.user1(request.getSender())
			.user2(request.getReceiver())
			.build();
		friendshipRepository.save(friendship);

		log.info("Friend request accepted: {} ← {}", receiverId, request.getSender().getId());

		Profile senderProfile = profileRepository.findByUserId(request.getSender().getId()).orElse(null);
		Profile receiverProfile = profileRepository.findByUserId(receiverId).orElse(null);

		String receiverNickname = receiverProfile != null ? receiverProfile.getNickname() : "누군가";
		fcmService.sendToUserAsync(request.getSender().getId(), "친구 수락", receiverNickname + "님이 친구 요청을 수락했어요");

		return FriendRequestResponse.from(request, senderProfile, receiverProfile);
	}

	/**
	 * 친구 요청 거절
	 */
	@Transactional
	public void rejectFriendRequest(Long receiverId, Long requestId) {
		FriendRequest request = friendRequestRepository.findByIdAndReceiverId(requestId, receiverId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

		if (!request.isPending()) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}

		request.reject();
		log.info("Friend request rejected: {} ← {}", receiverId, request.getSender().getId());
	}

	/**
	 * 내 친구 목록 조회
	 */
	@Transactional(readOnly = true)
	public List<FriendResponse> getFriends(Long userId) {
		List<Friendship> friendships = friendshipRepository.findAllByUserId(userId);
		return friendships.stream()
			.map(friendship -> {
				Long friendId = friendship.getOtherUser(userId).getId();
				Profile friendProfile = profileRepository.findByUserId(friendId).orElse(null);
				return FriendResponse.from(friendship, userId, friendProfile);
			})
			.toList();
	}

	/**
	 * 받은 친구 요청 목록 조회 (PENDING)
	 */
	@Transactional(readOnly = true)
	public List<FriendRequestResponse> getPendingRequests(Long userId) {
		return friendRequestRepository.findByReceiverIdAndStatus(userId, FriendRequestStatus.PENDING)
			.stream()
			.map(req -> {
				Profile senderProfile = profileRepository.findByUserId(req.getSender().getId()).orElse(null);
				Profile receiverProfile = profileRepository.findByUserId(userId).orElse(null);
				return FriendRequestResponse.from(req, senderProfile, receiverProfile);
			})
			.toList();
	}

	/**
	 * 두 사용자가 친구인지 확인
	 */
	@Transactional(readOnly = true)
	public boolean areFriends(Long userId1, Long userId2) {
		return friendshipRepository.existsBetween(userId1, userId2);
	}
}
