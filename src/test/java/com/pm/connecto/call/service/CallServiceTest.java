package com.pm.connecto.call.service;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.connecto.call.dto.FriendCallResponse;
import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.friend.repository.FriendshipRepository;
import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.repository.CallSessionRepository;
import com.pm.connecto.notification.service.FcmService;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CallService 단위 테스트")
class CallServiceTest {

	@Mock
	private CallSessionRepository callSessionRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FriendshipRepository friendshipRepository;

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private FcmService fcmService;

	@InjectMocks
	private CallService callService;

	private static final Long SESSION_ID = 1L;
	private static final Long USER1_ID = 1L;
	private static final Long USER2_ID = 2L;

	/**
	 * User는 @GeneratedValue로 ID가 설정되므로 Mock을 사용
	 */
	private User mockUser(Long id) {
		User user = mock(User.class);
		lenient().when(user.getId()).thenReturn(id);
		return user;
	}

	private CallSession createInProgressSession(User user1, User user2) {
		CallSession session = new CallSession(user1, user2);
		session.start("webrtc-channel-id");
		return session;
	}

	private CallSession createEndedSession(User user1, User user2) {
		CallSession session = new CallSession(user1, user2);
		session.start("webrtc-channel-id");
		session.end();
		return session;
	}

	@Nested
	@DisplayName("통화 종료 (endCall)")
	class EndCallTest {

		@Test
		@DisplayName("성공: IN_PROGRESS 상태의 세션을 종료한다")
		void 통화_종료_성공() {
			// given
			User user1 = mockUser(USER1_ID);
			User user2 = mockUser(USER2_ID);
			CallSession session = createInProgressSession(user1, user2);
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER1_ID)).willReturn(Optional.of(session));

			// when
			callService.endCall(SESSION_ID, USER1_ID);

			// then
			assertThat(session.isEnded()).isTrue();
			assertThat(session.getEndedAt()).isNotNull();
		}

		@Test
		@DisplayName("실패: 세션이 존재하지 않으면 ResourceNotFoundException 발생")
		void 세션_없음_예외() {
			// given
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER1_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> callService.endCall(SESSION_ID, USER1_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_FOUND);
		}

		@Test
		@DisplayName("실패: 이미 종료된 세션을 종료하려 하면 ForbiddenException 발생")
		void 이미_종료된_세션_예외() {
			// given
			User user1 = mockUser(USER1_ID);
			User user2 = mockUser(USER2_ID);
			CallSession session = createEndedSession(user1, user2);
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER1_ID)).willReturn(Optional.of(session));

			// when & then
			assertThatThrownBy(() -> callService.endCall(SESSION_ID, USER1_ID))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
		}
	}

	@Nested
	@DisplayName("재연결 의사 표현 (expressCallAgain)")
	class ExpressCallAgainTest {

		@Test
		@DisplayName("성공: 종료된 세션에서 user1이 재연결 의사를 표현한다")
		void user1_재연결_의사_표현_성공() {
			// given
			User user1 = mockUser(USER1_ID);
			User user2 = mockUser(USER2_ID);
			CallSession session = createEndedSession(user1, user2);
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER1_ID)).willReturn(Optional.of(session));

			// when
			callService.expressCallAgain(SESSION_ID, USER1_ID, true);

			// then
			assertThat(session.getUser1WantAgain()).isTrue();
		}

		@Test
		@DisplayName("성공: 종료된 세션에서 user2가 재연결 의사를 표현한다")
		void user2_재연결_의사_표현_성공() {
			// given
			User user1 = mockUser(USER1_ID);
			User user2 = mockUser(USER2_ID);
			CallSession session = createEndedSession(user1, user2);
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER2_ID)).willReturn(Optional.of(session));

			// when
			callService.expressCallAgain(SESSION_ID, USER2_ID, true);

			// then
			assertThat(session.getUser2WantAgain()).isTrue();
		}

		@Test
		@DisplayName("실패: IN_PROGRESS 세션에서 재연결 의사 표현 시 ForbiddenException 발생")
		void 진행_중_세션_재연결_예외() {
			// given
			User user1 = mockUser(USER1_ID);
			User user2 = mockUser(USER2_ID);
			CallSession session = createInProgressSession(user1, user2);
			given(callSessionRepository.findByIdAndUserId(SESSION_ID, USER1_ID)).willReturn(Optional.of(session));

			// when & then
			assertThatThrownBy(() -> callService.expressCallAgain(SESSION_ID, USER1_ID, true))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
		}
	}

	@Nested
	@DisplayName("친구 통화 요청 (requestCallToFriend)")
	class RequestCallToFriendTest {

		@Test
		@DisplayName("성공: 친구에게 통화 요청을 보낸다")
		void 친구_통화_요청_성공() {
			// given
			User caller = mockUser(USER1_ID);
			User friend = mockUser(USER2_ID);
			given(friendshipRepository.existsBetween(USER1_ID, USER2_ID)).willReturn(true);
			given(userRepository.findActiveById(USER1_ID)).willReturn(Optional.of(caller));
			given(userRepository.findActiveById(USER2_ID)).willReturn(Optional.of(friend));
			given(callSessionRepository.findInProgressByUserId(USER1_ID)).willReturn(Optional.empty());
			given(callSessionRepository.save(any(CallSession.class))).willAnswer(inv -> inv.getArgument(0));

			// when
			FriendCallResponse response = callService.requestCallToFriend(USER1_ID, USER2_ID);

			// then
			assertThat(response).isNotNull();
			assertThat(response.friendId()).isEqualTo(USER2_ID);
			assertThat(response.webrtcChannelId()).isNotBlank();
		}

		@Test
		@DisplayName("실패: 친구 관계가 아니면 ForbiddenException 발생")
		void 친구_아님_예외() {
			// given
			given(friendshipRepository.existsBetween(USER1_ID, USER2_ID)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> callService.requestCallToFriend(USER1_ID, USER2_ID))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
		}

		@Test
		@DisplayName("실패: 이미 통화 중이면 DuplicateResourceException 발생")
		void 이미_통화_중_예외() {
			// given
			User caller = mockUser(USER1_ID);
			User friend = mockUser(USER2_ID);
			CallSession existingSession = createInProgressSession(caller, friend);
			given(friendshipRepository.existsBetween(USER1_ID, USER2_ID)).willReturn(true);
			given(userRepository.findActiveById(USER1_ID)).willReturn(Optional.of(caller));
			given(userRepository.findActiveById(USER2_ID)).willReturn(Optional.of(friend));
			given(callSessionRepository.findInProgressByUserId(USER1_ID)).willReturn(Optional.of(existingSession));

			// when & then
			assertThatThrownBy(() -> callService.requestCallToFriend(USER1_ID, USER2_ID))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_IN_CALL);
		}
	}
}
