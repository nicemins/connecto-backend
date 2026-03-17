package com.pm.connecto.friend.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.friend.domain.FriendRequest;
import com.pm.connecto.friend.domain.FriendRequestStatus;
import com.pm.connecto.friend.domain.Friendship;
import com.pm.connecto.friend.dto.FriendRequestResponse;
import com.pm.connecto.friend.repository.FriendRequestRepository;
import com.pm.connecto.friend.repository.FriendshipRepository;
import com.pm.connecto.notification.service.FcmService;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendService 단위 테스트")
class FriendServiceTest {

	@Mock
	private FriendRequestRepository friendRequestRepository;

	@Mock
	private FriendshipRepository friendshipRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private FcmService fcmService;

	@InjectMocks
	private FriendService friendService;

	private static final Long SENDER_ID = 1L;
	private static final Long RECEIVER_ID = 2L;
	private static final Long REQUEST_ID = 10L;

	private User createUser(String email) {
		return new User(email, "encodedPassword");
	}

	@Nested
	@DisplayName("친구 요청 전송 (sendFriendRequest)")
	class SendFriendRequestTest {

		@Test
		@DisplayName("성공: 친구 요청을 전송한다")
		void 친구_요청_전송_성공() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			given(userRepository.findActiveById(SENDER_ID)).willReturn(Optional.of(sender));
			given(userRepository.findActiveById(RECEIVER_ID)).willReturn(Optional.of(receiver));
			given(friendRequestRepository.existsActiveRequestBetween(SENDER_ID, RECEIVER_ID)).willReturn(false);
			given(friendRequestRepository.save(any(FriendRequest.class))).willAnswer(inv -> inv.getArgument(0));
			given(profileRepository.findByUserId(SENDER_ID)).willReturn(Optional.empty());
			given(profileRepository.findByUserId(RECEIVER_ID)).willReturn(Optional.empty());

			// when
			FriendRequestResponse result = friendService.sendFriendRequest(SENDER_ID, RECEIVER_ID);

			// then
			assertThat(result).isNotNull();
			assertThat(result.status()).isEqualTo(FriendRequestStatus.PENDING);
			verify(friendRequestRepository).save(any(FriendRequest.class));
		}

		@Test
		@DisplayName("실패: 자기 자신에게 친구 요청 시 ForbiddenException 발생")
		void 자기_자신_친구_요청_예외() {
			// when & then
			assertThatThrownBy(() -> friendService.sendFriendRequest(SENDER_ID, SENDER_ID))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

			verify(friendRequestRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 보내는 사람이 존재하지 않으면 ResourceNotFoundException 발생")
		void 보내는_사람_없음_예외() {
			// given
			given(userRepository.findActiveById(SENDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> friendService.sendFriendRequest(SENDER_ID, RECEIVER_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}

		@Test
		@DisplayName("실패: 이미 요청이 존재하면 DuplicateResourceException 발생")
		void 중복_친구_요청_예외() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			given(userRepository.findActiveById(SENDER_ID)).willReturn(Optional.of(sender));
			given(userRepository.findActiveById(RECEIVER_ID)).willReturn(Optional.of(receiver));
			given(friendRequestRepository.existsActiveRequestBetween(SENDER_ID, RECEIVER_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> friendService.sendFriendRequest(SENDER_ID, RECEIVER_ID))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_FRIEND_REQUEST);
		}
	}

	@Nested
	@DisplayName("친구 요청 수락 (acceptFriendRequest)")
	class AcceptFriendRequestTest {

		@Test
		@DisplayName("성공: PENDING 상태의 친구 요청을 수락하고 Friendship을 생성한다")
		void 친구_요청_수락_성공() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			FriendRequest request = FriendRequest.builder().sender(sender).receiver(receiver).build();
			given(friendRequestRepository.findByIdAndReceiverId(REQUEST_ID, RECEIVER_ID)).willReturn(Optional.of(request));
			given(friendshipRepository.existsBetween(sender.getId(), RECEIVER_ID)).willReturn(false);
			given(friendshipRepository.save(any(Friendship.class))).willAnswer(inv -> inv.getArgument(0));
			given(profileRepository.findByUserId(any())).willReturn(Optional.empty());

			// when
			FriendRequestResponse result = friendService.acceptFriendRequest(RECEIVER_ID, REQUEST_ID);

			// then
			assertThat(result.status()).isEqualTo(FriendRequestStatus.ACCEPTED);
			verify(friendshipRepository).save(any(Friendship.class));
		}

		@Test
		@DisplayName("실패: 친구 요청이 존재하지 않으면 ResourceNotFoundException 발생")
		void 친구_요청_없음_예외() {
			// given
			given(friendRequestRepository.findByIdAndReceiverId(REQUEST_ID, RECEIVER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> friendService.acceptFriendRequest(RECEIVER_ID, REQUEST_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FRIEND_REQUEST_NOT_FOUND);
		}

		@Test
		@DisplayName("실패: 이미 ACCEPTED된 요청을 다시 수락하면 ForbiddenException 발생")
		void 이미_수락된_요청_예외() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			FriendRequest request = FriendRequest.builder().sender(sender).receiver(receiver).build();
			request.accept(); // 이미 수락됨
			given(friendRequestRepository.findByIdAndReceiverId(REQUEST_ID, RECEIVER_ID)).willReturn(Optional.of(request));

			// when & then
			assertThatThrownBy(() -> friendService.acceptFriendRequest(RECEIVER_ID, REQUEST_ID))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
		}
	}

	@Nested
	@DisplayName("친구 요청 거절 (rejectFriendRequest)")
	class RejectFriendRequestTest {

		@Test
		@DisplayName("성공: PENDING 상태의 친구 요청을 거절한다")
		void 친구_요청_거절_성공() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			FriendRequest request = FriendRequest.builder().sender(sender).receiver(receiver).build();
			given(friendRequestRepository.findByIdAndReceiverId(REQUEST_ID, RECEIVER_ID)).willReturn(Optional.of(request));

			// when
			friendService.rejectFriendRequest(RECEIVER_ID, REQUEST_ID);

			// then
			assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
		}

		@Test
		@DisplayName("실패: PENDING이 아닌 요청을 거절하면 ForbiddenException 발생")
		void 이미_처리된_요청_거절_예외() {
			// given
			User sender = createUser("sender@example.com");
			User receiver = createUser("receiver@example.com");
			FriendRequest request = FriendRequest.builder().sender(sender).receiver(receiver).build();
			request.reject(); // 이미 거절됨
			given(friendRequestRepository.findByIdAndReceiverId(REQUEST_ID, RECEIVER_ID)).willReturn(Optional.of(request));

			// when & then
			assertThatThrownBy(() -> friendService.rejectFriendRequest(RECEIVER_ID, REQUEST_ID))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
		}
	}

	@Nested
	@DisplayName("친구 목록 조회 (getFriends)")
	class GetFriendsTest {

		@Test
		@DisplayName("성공: 친구 목록을 반환한다")
		void 친구_목록_조회_성공() {
			// given
			given(friendshipRepository.findAllByUserId(SENDER_ID)).willReturn(List.of());

			// when
			var result = friendService.getFriends(SENDER_ID);

			// then
			assertThat(result).isEmpty();
		}
	}
}
