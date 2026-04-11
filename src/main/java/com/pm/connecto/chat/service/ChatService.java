package com.pm.connecto.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.chat.domain.ChatMessage;
import com.pm.connecto.chat.domain.ChatRoom;
import com.pm.connecto.chat.domain.ChatRoomMember;
import com.pm.connecto.chat.domain.MessageType;
import com.pm.connecto.chat.dto.ChatMessagePageResponse;
import com.pm.connecto.chat.dto.ChatMessageResponse;
import com.pm.connecto.chat.dto.ChatRoomResponse;
import com.pm.connecto.chat.repository.ChatMessageRepository;
import com.pm.connecto.chat.repository.ChatRoomMemberRepository;
import com.pm.connecto.chat.repository.ChatRoomRepository;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.friend.repository.BlockRepository;
import com.pm.connecto.friend.repository.FriendshipRepository;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);
	private static final int MAX_PAGE_SIZE = 100;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final FriendshipRepository friendshipRepository;
	private final BlockRepository blockRepository;
	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;

	public ChatService(
		ChatRoomRepository chatRoomRepository,
		ChatMessageRepository chatMessageRepository,
		ChatRoomMemberRepository chatRoomMemberRepository,
		FriendshipRepository friendshipRepository,
		BlockRepository blockRepository,
		ProfileRepository profileRepository,
		UserRepository userRepository
	) {
		this.chatRoomRepository = chatRoomRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.chatRoomMemberRepository = chatRoomMemberRepository;
		this.friendshipRepository = friendshipRepository;
		this.blockRepository = blockRepository;
		this.profileRepository = profileRepository;
		this.userRepository = userRepository;
	}

	public record RoomResult(ChatRoomResponse response, boolean created) {}

	/**
	 * 채팅방 생성 또는 기존 채팅방 반환 (친구 사이에만 가능)
	 * created=true → 신규 생성(201), created=false → 기존 반환(200)
	 */
	@Transactional
	public RoomResult createOrGetRoom(Long userId, Long friendId) {
		if (!friendshipRepository.existsBetween(userId, friendId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		Optional<ChatRoom> existing = chatRoomRepository.findBetween(userId, friendId);
		if (existing.isPresent()) {
			return new RoomResult(toResponse(existing.get(), userId), false);
		}
		try {
			User user1 = userRepository.getReferenceById(userId);
			User user2 = userRepository.getReferenceById(friendId);
			ChatRoom room = chatRoomRepository.save(new ChatRoom(user1, user2));
			log.info("Chat room created: {} between users {} and {}", room.getId(), userId, friendId);
			return new RoomResult(toResponse(room, userId), true);
		} catch (DataIntegrityViolationException e) {
			// 동시 요청으로 인한 uk_chat_room 충돌 — 이미 생성된 방 반환
			return chatRoomRepository.findBetween(userId, friendId)
				.map(r -> new RoomResult(toResponse(r, userId), false))
				.orElseThrow(() -> new IllegalStateException("Chat room creation race condition unresolved"));
		}
	}

	/**
	 * 내 채팅방 목록 조회 (최신순)
	 */
	@Transactional(readOnly = true)
	public List<ChatRoomResponse> getRooms(Long userId) {
		List<ChatRoom> rooms = chatRoomRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);
		if (rooms.isEmpty()) return List.of();

		List<Long> friendIds = rooms.stream().map(r -> r.getOtherUser(userId).getId()).toList();
		List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();

		Map<Long, Profile> profileByUserId = profileRepository.findByUserIdIn(friendIds).stream()
			.collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

		Map<Long, String> lastMessageByRoomId = chatMessageRepository.findLatestMessageContentByRoomIds(roomIds).stream()
			.collect(Collectors.toMap(
				row -> ((Number) row[0]).longValue(),
				row -> (String) row[1]
			));

		// unreadCount 계산: 내 ChatRoomMember 일괄 조회
		Map<Long, Long> lastReadByRoomId = chatRoomMemberRepository.findByUserIdAndRoomIdIn(userId, roomIds).stream()
			.collect(Collectors.toMap(m -> m.getRoom().getId(), ChatRoomMember::getLastReadMessageId));

		return rooms.stream().map(room -> {
			Long friendId = room.getOtherUser(userId).getId();
			Profile friendProfile = profileByUserId.get(friendId);
			Long lastReadMessageId = lastReadByRoomId.get(room.getId());
			int unreadCount = lastReadMessageId != null
				? chatMessageRepository.countUnread(room.getId(), userId, lastReadMessageId)
				: chatMessageRepository.countAllUnread(room.getId(), userId);
			return new ChatRoomResponse(
				room.getId(),
				friendId,
				friendProfile != null ? friendProfile.getNickname() : null,
				friendProfile != null ? friendProfile.getProfileImageUrl() : null,
				lastMessageByRoomId.get(room.getId()),
				unreadCount,
				room.getUpdatedAt()
			);
		}).toList();
	}

	/**
	 * 메시지 히스토리 조회 (페이징, 최신 → 과거순)
	 */
	/**
	 * 메시지 히스토리 조회 + 자동 읽음 처리
	 * 반환값의 otherUserId: Controller에서 chat:read 소켓 emit에 사용
	 */
	public record MessagesResult(ChatMessagePageResponse page, Long otherUserId, Long lastReadMessageId) {}

	@Transactional
	public MessagesResult getMessages(Long userId, Long roomId, int page, int size) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		size = Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(page, size);
		Page<ChatMessage> msgPage = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);

		// 첫 페이지 조회 시 자동 읽음 처리
		Long lastReadMessageId = null;
		if (page == 0) {
			Long latestMessageId = chatMessageRepository.findMaxIdByRoomId(roomId).orElse(null);
			if (latestMessageId != null) {
				lastReadMessageId = upsertLastRead(room, userId, latestMessageId);
			}
		}

		ChatMessagePageResponse pageResponse = new ChatMessagePageResponse(
			msgPage.getContent().stream().map(ChatMessageResponse::from).toList(),
			msgPage.hasNext(),
			page,
			size
		);
		return new MessagesResult(pageResponse, room.getOtherUser(userId).getId(), lastReadMessageId);
	}

	/**
	 * 읽음 처리 (REST PATCH /read + 소켓 chat:read 공통)
	 */
	public record ReadResult(int unreadCount, Long otherUserId, Long lastReadMessageId) {}

	@Transactional
	public ReadResult markAsRead(Long roomId, Long userId, Long lastMessageId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		Long updatedId = upsertLastRead(room, userId, lastMessageId);
		int unreadCount = updatedId != null
			? chatMessageRepository.countUnread(roomId, userId, updatedId)
			: chatMessageRepository.countAllUnread(roomId, userId);
		return new ReadResult(unreadCount, room.getOtherUser(userId).getId(), updatedId);
	}

	/**
	 * 미읽음 카운트 단순 조회 (GET /unread)
	 */
	@Transactional(readOnly = true)
	public int getUnreadCount(Long roomId, Long userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		return chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId)
			.map(m -> m.getLastReadMessageId() != null
				? chatMessageRepository.countUnread(roomId, userId, m.getLastReadMessageId())
				: chatMessageRepository.countAllUnread(roomId, userId))
			.orElse(chatMessageRepository.countAllUnread(roomId, userId));
	}

	/**
	 * 소켓 핸들러용 읽음 처리 — 최신 메시지 ID 자동 조회 후 markAsRead
	 * 메시지가 없으면 null 반환
	 */
	@Transactional
	public ReadResult markAsReadLatest(Long roomId, Long userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		Long latestMessageId = chatMessageRepository.findMaxIdByRoomId(roomId).orElse(null);
		if (latestMessageId == null) return null;

		Long updatedId = upsertLastRead(room, userId, latestMessageId);
		int unreadCount = updatedId != null
			? chatMessageRepository.countUnread(roomId, userId, updatedId)
			: chatMessageRepository.countAllUnread(roomId, userId);
		return new ReadResult(unreadCount, room.getOtherUser(userId).getId(), updatedId);
	}

	/**
	 * ChatRoomMember upsert — 없으면 생성, 있으면 updateLastRead
	 * @return 실제로 저장된 lastReadMessageId (null이면 업데이트 안 됨)
	 */
	private Long upsertLastRead(ChatRoom room, Long userId, Long messageId) {
		ChatRoomMember member = chatRoomMemberRepository
			.findByRoomIdAndUserId(room.getId(), userId)
			.orElseGet(() -> {
				User user = userRepository.getReferenceById(userId);
				return chatRoomMemberRepository.save(new ChatRoomMember(room, user));
			});
		member.updateLastRead(messageId);
		return member.getLastReadMessageId();
	}

	/**
	 * 채팅방의 상대방 userId 조회 (소켓 핸들러에서 호출)
	 * - 멤버 인증 포함
	 */
	@Transactional(readOnly = true)
	public Long getOtherUserId(Long roomId, Long userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		return room.getOtherUser(userId).getId();
	}

	/**
	 * 메시지 저장 (소켓 핸들러에서 호출)
	 * - 채팅방 멤버 인증, 차단 여부 확인 후 저장
	 */
	public record SavedMessage(ChatMessageResponse messageResponse, Long senderId, Long otherUserId) {}

	@Transactional
	public SavedMessage saveMessage(Long roomId, Long senderId, String content) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(senderId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		Long otherUserId = room.getOtherUser(senderId).getId();
		if (blockRepository.existsBlockBetween(senderId, otherUserId)) {
			throw new ForbiddenException(ErrorCode.MESSAGE_BLOCKED);
		}
		User sender = userRepository.getReferenceById(senderId);
		ChatMessage msg = chatMessageRepository.save(new ChatMessage(room, sender, content));
		room.updateTimestamp(msg.getCreatedAt());
		// DTO 변환을 트랜잭션 내에서 수행 — LAZY 접근 방지
		return new SavedMessage(ChatMessageResponse.from(msg, senderId), senderId, otherUserId);
	}

	@Transactional
	public SavedMessage saveImageMessage(Long roomId, Long senderId, String imageUrl) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(senderId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		Long otherUserId = room.getOtherUser(senderId).getId();
		if (blockRepository.existsBlockBetween(senderId, otherUserId)) {
			throw new ForbiddenException(ErrorCode.MESSAGE_BLOCKED);
		}
		User sender = userRepository.getReferenceById(senderId);
		ChatMessage msg = chatMessageRepository.save(new ChatMessage(room, sender, imageUrl, MessageType.IMAGE));
		room.updateTimestamp(msg.getCreatedAt());
		return new SavedMessage(ChatMessageResponse.from(msg, senderId), senderId, otherUserId);
	}

	/**
	 * 채팅방 나가기 — 요청 유저 기준 숨김 처리 (상대방 채팅방 유지)
	 * 이미 나간 방 재요청 시 200 OK (멱등성 보장)
	 */
	@Transactional
	public void leaveRoom(Long roomId, Long userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isMember(userId)) {
			throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
		}
		if (!room.hasLeft(userId)) {
			room.leave(userId);
		}
	}

	private ChatRoomResponse toResponse(ChatRoom room, Long userId) {
		Long friendId = room.getOtherUser(userId).getId();
		Profile friendProfile = profileRepository.findByUserId(friendId).orElse(null);
		String lastMessage = chatMessageRepository
			.findTopByRoomIdOrderByCreatedAtDesc(room.getId())
			.map(msg -> MessageType.IMAGE == msg.getMessageType() ? "사진" : msg.getContent())
			.orElse(null);
		int unreadCount = chatRoomMemberRepository.findByRoomIdAndUserId(room.getId(), userId)
			.map(m -> m.getLastReadMessageId() != null
				? chatMessageRepository.countUnread(room.getId(), userId, m.getLastReadMessageId())
				: chatMessageRepository.countAllUnread(room.getId(), userId))
			.orElse(chatMessageRepository.countAllUnread(room.getId(), userId));
		return new ChatRoomResponse(
			room.getId(),
			friendId,
			friendProfile != null ? friendProfile.getNickname() : null,
			friendProfile != null ? friendProfile.getProfileImageUrl() : null,
			lastMessage,
			unreadCount,
			room.getUpdatedAt()
		);
	}
}
