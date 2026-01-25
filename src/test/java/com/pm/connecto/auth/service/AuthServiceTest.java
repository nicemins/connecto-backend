package com.pm.connecto.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private AuthService authService;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_PASSWORD = "password123";
	private static final String ENCODED_PASSWORD = "encodedPassword123";
	private static final Long TEST_USER_ID = 1L;
	private static final String ACCESS_TOKEN = "accessToken123";
	private static final String REFRESH_TOKEN = "refreshToken123";

	@Nested
	@DisplayName("로그인 인증 (authenticate)")
	class AuthenticateTest {

		@Test
		@DisplayName("성공: 올바른 이메일과 비밀번호로 로그인하면 사용자 정보가 반환된다")
		void 정상_로그인_성공() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			given(userRepository.findByEmailForAuth(TEST_EMAIL)).willReturn(Optional.of(user));
			given(passwordEncoder.matches(TEST_PASSWORD, ENCODED_PASSWORD)).willReturn(true);

			// when
			User result = authService.authenticate(TEST_EMAIL, TEST_PASSWORD);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
			verify(userRepository).findByEmailForAuth(TEST_EMAIL);
			verify(passwordEncoder).matches(TEST_PASSWORD, ENCODED_PASSWORD);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 이메일로 로그인하면 ResourceNotFoundException 발생")
		void 존재하지_않는_이메일_로그인_실패() {
			// given
			given(userRepository.findByEmailForAuth(TEST_EMAIL)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.authenticate(TEST_EMAIL, TEST_PASSWORD))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}

		@Test
		@DisplayName("실패: 잘못된 비밀번호로 로그인하면 UnauthorizedException 발생")
		void 잘못된_비밀번호_로그인_실패() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			given(userRepository.findByEmailForAuth(TEST_EMAIL)).willReturn(Optional.of(user));
			given(passwordEncoder.matches(TEST_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> authService.authenticate(TEST_EMAIL, TEST_PASSWORD))
				.isInstanceOf(UnauthorizedException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);
		}

		@Test
		@DisplayName("실패: 탈퇴한 사용자가 로그인하면 UnauthorizedException 발생")
		void 탈퇴_사용자_로그인_실패() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			user.delete();  // Soft Delete 처리
			given(userRepository.findByEmailForAuth(TEST_EMAIL)).willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> authService.authenticate(TEST_EMAIL, TEST_PASSWORD))
				.isInstanceOf(UnauthorizedException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELETED_USER);
		}

		@Test
		@DisplayName("실패: 차단된 사용자가 로그인하면 ForbiddenException 발생")
		void 차단_사용자_로그인_실패() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			user.block();  // 사용자 차단
			given(userRepository.findByEmailForAuth(TEST_EMAIL)).willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> authService.authenticate(TEST_EMAIL, TEST_PASSWORD))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.BLOCKED_USER);
		}
	}

	@Nested
	@DisplayName("토큰 생성")
	class TokenGenerationTest {

		@Test
		@DisplayName("성공: Access Token 생성")
		void 액세스_토큰_생성_성공() {
			// given
			given(jwtTokenProvider.generateAccessToken(TEST_USER_ID)).willReturn(ACCESS_TOKEN);

			// when
			String result = authService.generateAccessToken(TEST_USER_ID);

			// then
			assertThat(result).isEqualTo(ACCESS_TOKEN);
			verify(jwtTokenProvider).generateAccessToken(TEST_USER_ID);
		}

		@Test
		@DisplayName("성공: Refresh Token 생성")
		void 리프레시_토큰_생성_성공() {
			// given
			given(jwtTokenProvider.generateRefreshToken(TEST_USER_ID)).willReturn(REFRESH_TOKEN);

			// when
			String result = authService.generateRefreshToken(TEST_USER_ID);

			// then
			assertThat(result).isEqualTo(REFRESH_TOKEN);
			verify(jwtTokenProvider).generateRefreshToken(TEST_USER_ID);
		}
	}

	@Nested
	@DisplayName("토큰 갱신 (refreshAccessToken)")
	class RefreshAccessTokenTest {

		@Test
		@DisplayName("성공: 유효한 Refresh Token으로 새 Access Token 발급")
		void 유효한_리프레시_토큰으로_갱신_성공() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			given(jwtTokenProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
			given(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).willReturn(TEST_USER_ID);
			given(userRepository.findByIdForAuth(TEST_USER_ID)).willReturn(Optional.of(user));
			given(jwtTokenProvider.generateAccessToken(TEST_USER_ID)).willReturn(ACCESS_TOKEN);

			// when
			String result = authService.refreshAccessToken(REFRESH_TOKEN);

			// then
			assertThat(result).isEqualTo(ACCESS_TOKEN);
			verify(jwtTokenProvider).validateToken(REFRESH_TOKEN);
			verify(jwtTokenProvider).getUserIdFromToken(REFRESH_TOKEN);
			verify(userRepository).findByIdForAuth(TEST_USER_ID);
		}

		@Test
		@DisplayName("실패: 유효하지 않은 Refresh Token으로 갱신 시 UnauthorizedException 발생")
		void 유효하지_않은_리프레시_토큰_갱신_실패() {
			// given
			given(jwtTokenProvider.validateToken(REFRESH_TOKEN)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> authService.refreshAccessToken(REFRESH_TOKEN))
				.isInstanceOf(UnauthorizedException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
		}

		@Test
		@DisplayName("실패: 탈퇴한 사용자의 Refresh Token으로 갱신 시 UnauthorizedException 발생")
		void 탈퇴_사용자_토큰_갱신_실패() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			user.delete();
			given(jwtTokenProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
			given(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).willReturn(TEST_USER_ID);
			given(userRepository.findByIdForAuth(TEST_USER_ID)).willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> authService.refreshAccessToken(REFRESH_TOKEN))
				.isInstanceOf(UnauthorizedException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELETED_USER);
		}

		@Test
		@DisplayName("실패: 차단된 사용자의 Refresh Token으로 갱신 시 ForbiddenException 발생")
		void 차단_사용자_토큰_갱신_실패() {
			// given
			User user = new User(TEST_EMAIL, ENCODED_PASSWORD);
			user.block();
			given(jwtTokenProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
			given(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).willReturn(TEST_USER_ID);
			given(userRepository.findByIdForAuth(TEST_USER_ID)).willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> authService.refreshAccessToken(REFRESH_TOKEN))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.BLOCKED_USER);
		}
	}
}
