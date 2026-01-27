package com.pm.connecto.user.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_PASSWORD = "password123";
	private static final String ENCODED_PASSWORD = "encodedPassword123";

	@Nested
	@DisplayName("회원가입 (createUser)")
	class CreateUserTest {

		@Test
		@DisplayName("성공: 신규 이메일로 회원가입하면 사용자가 생성된다")
		void 신규_이메일로_회원가입_성공() {
			// given
			given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);
			given(passwordEncoder.encode(TEST_PASSWORD)).willReturn(ENCODED_PASSWORD);
			given(userRepository.save(any(User.class))).willAnswer(invocation -> {
				User user = invocation.getArgument(0);
				return user;
			});

			// when
			User result = userService.createUser(TEST_EMAIL, TEST_PASSWORD);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
			assertThat(result.getPassword()).isEqualTo(ENCODED_PASSWORD);
			verify(userRepository).existsByEmail(TEST_EMAIL);
			verify(passwordEncoder).encode(TEST_PASSWORD);
			verify(userRepository).save(any(User.class));
		}

		@Test
		@DisplayName("실패: 이미 존재하는 이메일로 회원가입하면 DuplicateResourceException 발생")
		void 중복_이메일로_회원가입_실패() {
			// given
			given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> userService.createUser(TEST_EMAIL, TEST_PASSWORD))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

			verify(userRepository).existsByEmail(TEST_EMAIL);
			verify(passwordEncoder, never()).encode(anyString());
			verify(userRepository, never()).save(any(User.class));
		}
	}

	@Nested
	@DisplayName("내 정보 조회 (getMe)")
	class GetMeTest {

		@Test
		@DisplayName("성공: 활성 사용자 ID로 조회하면 사용자 정보가 반환된다")
		void 활성_사용자_조회_성공() {
			// given
			Long userId = 1L;
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			given(userRepository.findActiveById(userId)).willReturn(Optional.of(user));

			// when
			User result = userService.getMe(userId);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
			verify(userRepository).findActiveById(userId);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 사용자 ID로 조회하면 ResourceNotFoundException 발생")
		void 존재하지_않는_사용자_조회_실패() {
			// given
			Long userId = 999L;
			given(userRepository.findActiveById(userId)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> userService.getMe(userId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("이메일 사용 가능 여부 확인 (isEmailAvailable)")
	class IsEmailAvailableTest {

		@Test
		@DisplayName("성공: 사용 가능한 이메일이면 true 반환")
		void 사용_가능한_이메일_확인() {
			// given
			given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);

			// when
			boolean result = userService.isEmailAvailable(TEST_EMAIL);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("성공: 이미 사용 중인 이메일이면 false 반환")
		void 사용_중인_이메일_확인() {
			// given
			given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(true);

			// when
			boolean result = userService.isEmailAvailable(TEST_EMAIL);

			// then
			assertThat(result).isFalse();
		}
	}

	@Nested
	@DisplayName("회원 탈퇴 (deleteUser)")
	class DeleteUserTest {

		@Test
		@DisplayName("성공: 활성 사용자가 탈퇴하면 Soft Delete 처리된다")
		void 회원_탈퇴_성공() {
			// given
			Long userId = 1L;
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			given(userRepository.findActiveById(userId)).willReturn(Optional.of(user));

			// when
			userService.deleteUser(userId);

			// then
			assertThat(user.isDeleted()).isTrue();
			assertThat(user.getDeletedAt()).isNotNull();
		}

		@Test
		@DisplayName("실패: 존재하지 않는 사용자 탈퇴 시 ResourceNotFoundException 발생")
		void 존재하지_않는_사용자_탈퇴_실패() {
			// given
			Long userId = 999L;
			given(userRepository.findActiveById(userId)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> userService.deleteUser(userId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}
}
