package com.pm.connecto.profile.service;

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
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.common.service.S3Service;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService 단위 테스트")
class ProfileServiceTest {

	@Mock
	private ProfileRepository profileRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private S3Service s3Service;

	@InjectMocks
	private ProfileService profileService;

	private static final Long USER_ID = 1L;
	private static final String NICKNAME = "testuser";
	private static final String BIO = "안녕하세요";

	private User createUser() {
		return new User("test@example.com", "encodedPassword");
	}

	private Profile createProfile(User user) {
		return Profile.builder()
			.user(user)
			.nickname(NICKNAME)
			.profileImageUrl(null)
			.bio(BIO)
			.build();
	}

	@Nested
	@DisplayName("프로필 생성 (createProfile)")
	class CreateProfileTest {

		@Test
		@DisplayName("성공: 정상 입력으로 프로필을 생성한다")
		void 프로필_생성_성공() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(profileRepository.existsByUserId(USER_ID)).willReturn(false);
			given(profileRepository.existsByNickname(NICKNAME)).willReturn(false);
			given(profileRepository.save(any(Profile.class))).willAnswer(inv -> inv.getArgument(0));

			// when
			Profile result = profileService.createProfile(USER_ID, NICKNAME, null, BIO);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getNickname()).isEqualTo(NICKNAME);
			assertThat(result.getBio()).isEqualTo(BIO);
			verify(profileRepository).save(any(Profile.class));
		}

		@Test
		@DisplayName("실패: 존재하지 않는 사용자이면 ResourceNotFoundException 발생")
		void 사용자_없음_예외() {
			// given
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> profileService.createProfile(USER_ID, NICKNAME, null, BIO))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

			verify(profileRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 이미 프로필이 존재하면 DuplicateResourceException 발생")
		void 프로필_중복_예외() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(profileRepository.existsByUserId(USER_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> profileService.createProfile(USER_ID, NICKNAME, null, BIO))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PROFILE);
		}

		@Test
		@DisplayName("실패: 닉네임이 중복이면 DuplicateResourceException 발생")
		void 닉네임_중복_예외() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(profileRepository.existsByUserId(USER_ID)).willReturn(false);
			given(profileRepository.existsByNickname(NICKNAME)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> profileService.createProfile(USER_ID, NICKNAME, null, BIO))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
		}
	}

	@Nested
	@DisplayName("프로필 조회 (getProfile)")
	class GetProfileTest {

		@Test
		@DisplayName("성공: 존재하는 사용자의 프로필을 조회한다")
		void 프로필_조회_성공() {
			// given
			User user = createUser();
			Profile profile = createProfile(user);
			given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

			// when
			Profile result = profileService.getProfile(USER_ID);

			// then
			assertThat(result.getNickname()).isEqualTo(NICKNAME);
		}

		@Test
		@DisplayName("실패: 프로필이 없으면 ResourceNotFoundException 발생")
		void 프로필_없음_예외() {
			// given
			given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> profileService.getProfile(USER_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROFILE_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("프로필 수정 (updateProfile)")
	class UpdateProfileTest {

		@Test
		@DisplayName("성공: 닉네임과 바이오를 수정한다")
		void 프로필_수정_성공() {
			// given
			User user = createUser();
			Profile profile = createProfile(user);
			String newNickname = "newnick";
			String newBio = "새 소개";
			given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
			given(profileRepository.existsByNicknameAndUserIdNot(newNickname, USER_ID)).willReturn(false);

			// when
			Profile result = profileService.updateProfile(USER_ID, newNickname, null, newBio);

			// then
			assertThat(result.getNickname()).isEqualTo(newNickname);
			assertThat(result.getBio()).isEqualTo(newBio);
		}

		@Test
		@DisplayName("실패: 다른 사용자가 사용 중인 닉네임으로 수정하면 DuplicateResourceException 발생")
		void 닉네임_중복_수정_예외() {
			// given
			User user = createUser();
			Profile profile = createProfile(user);
			String takenNickname = "taken";
			given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
			given(profileRepository.existsByNicknameAndUserIdNot(takenNickname, USER_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> profileService.updateProfile(USER_ID, takenNickname, null, BIO))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
		}

		@Test
		@DisplayName("성공: 닉네임이 null이면 닉네임 중복 체크를 건너뛴다")
		void 닉네임_null_수정() {
			// given
			User user = createUser();
			Profile profile = createProfile(user);
			given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

			// when
			Profile result = profileService.updateProfile(USER_ID, null, null, "변경된 바이오");

			// then
			assertThat(result.getNickname()).isEqualTo(NICKNAME); // 변경 안 됨
			verify(profileRepository, never()).existsByNicknameAndUserIdNot(any(), any());
		}
	}

	@Nested
	@DisplayName("닉네임 중복 확인 (isNicknameAvailable)")
	class IsNicknameAvailableTest {

		@Test
		@DisplayName("성공: 사용 가능한 닉네임이면 true 반환")
		void 사용_가능한_닉네임() {
			// given
			given(profileRepository.existsByNickname(NICKNAME)).willReturn(false);

			// when & then
			assertThat(profileService.isNicknameAvailable(NICKNAME)).isTrue();
		}

		@Test
		@DisplayName("성공: 이미 사용 중인 닉네임이면 false 반환")
		void 사용_중인_닉네임() {
			// given
			given(profileRepository.existsByNickname(NICKNAME)).willReturn(true);

			// when & then
			assertThat(profileService.isNicknameAvailable(NICKNAME)).isFalse();
		}
	}
}
