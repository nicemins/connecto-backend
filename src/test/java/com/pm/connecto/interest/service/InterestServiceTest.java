package com.pm.connecto.interest.service;

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
import com.pm.connecto.common.exception.MaxLimitExceededException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.repository.InterestRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterestService 단위 테스트")
class InterestServiceTest {

	@Mock
	private InterestRepository interestRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private InterestService interestService;

	private static final Long USER_ID = 1L;
	private static final Long INTEREST_ID = 10L;
	private static final String TAG = "여행";

	private User createUser() {
		return new User("test@example.com", "encodedPassword");
	}

	@Nested
	@DisplayName("관심사 추가 (addInterest)")
	class AddInterestTest {

		@Test
		@DisplayName("성공: 유효한 태그로 관심사를 추가한다")
		void 관심사_추가_성공() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(interestRepository.existsByUserIdAndTag(USER_ID, TAG)).willReturn(false);
			given(interestRepository.countByUserId(USER_ID)).willReturn(3);
			given(interestRepository.save(any(Interest.class))).willAnswer(inv -> inv.getArgument(0));

			// when
			Interest result = interestService.addInterest(USER_ID, TAG);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getTag()).isEqualTo(TAG);
			verify(interestRepository).save(any(Interest.class));
		}

		@Test
		@DisplayName("실패: 존재하지 않는 사용자면 ResourceNotFoundException 발생")
		void 존재하지_않는_사용자_관심사_추가_실패() {
			// given
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> interestService.addInterest(USER_ID, TAG))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

			verify(interestRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 중복 태그이면 DuplicateResourceException 발생")
		void 중복_태그_관심사_추가_실패() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(interestRepository.existsByUserIdAndTag(USER_ID, TAG)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> interestService.addInterest(USER_ID, TAG))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_INTEREST);

			verify(interestRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 최대 10개 초과 시 MaxLimitExceededException 발생")
		void 최대_개수_초과_관심사_추가_실패() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(interestRepository.existsByUserIdAndTag(USER_ID, TAG)).willReturn(false);
			given(interestRepository.countByUserId(USER_ID)).willReturn(10);

			// when & then
			assertThatThrownBy(() -> interestService.addInterest(USER_ID, TAG))
				.isInstanceOf(MaxLimitExceededException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_LIMIT_EXCEEDED);

			verify(interestRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("관심사 목록 조회 (getInterests)")
	class GetInterestsTest {

		@Test
		@DisplayName("성공: 사용자의 관심사 목록을 반환한다")
		void 관심사_목록_조회_성공() {
			// given
			User user = createUser();
			Interest interest1 = Interest.builder().user(user).tag("여행").build();
			Interest interest2 = Interest.builder().user(user).tag("음악").build();
			given(interestRepository.findByUserId(USER_ID)).willReturn(List.of(interest1, interest2));

			// when
			List<Interest> result = interestService.getInterests(USER_ID);

			// then
			assertThat(result).hasSize(2);
			assertThat(result).extracting(Interest::getTag).containsExactly("여행", "음악");
		}

		@Test
		@DisplayName("성공: 관심사가 없으면 빈 목록을 반환한다")
		void 관심사_없음_빈_목록_반환() {
			// given
			given(interestRepository.findByUserId(USER_ID)).willReturn(List.of());

			// when
			List<Interest> result = interestService.getInterests(USER_ID);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("관심사 삭제 (deleteInterest)")
	class DeleteInterestTest {

		@Test
		@DisplayName("성공: 본인 관심사를 삭제한다")
		void 관심사_삭제_성공() {
			// given
			User user = createUser();
			Interest interest = Interest.builder().user(user).tag(TAG).build();
			given(interestRepository.findByIdAndUserId(INTEREST_ID, USER_ID)).willReturn(Optional.of(interest));

			// when
			interestService.deleteInterest(USER_ID, INTEREST_ID);

			// then
			verify(interestRepository).delete(interest);
		}

		@Test
		@DisplayName("실패: 존재하지 않거나 타인의 관심사 삭제 시 ResourceNotFoundException 발생")
		void 존재하지_않는_관심사_삭제_실패() {
			// given
			given(interestRepository.findByIdAndUserId(INTEREST_ID, USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> interestService.deleteInterest(USER_ID, INTEREST_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTEREST_NOT_FOUND);

			verify(interestRepository, never()).delete(any());
		}
	}
}
