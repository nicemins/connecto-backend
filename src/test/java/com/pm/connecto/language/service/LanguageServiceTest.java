package com.pm.connecto.language.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.language.dto.LanguageRequest.LanguageItem;
import com.pm.connecto.language.repository.LanguageRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("LanguageService 단위 테스트")
class LanguageServiceTest {

	@Mock
	private LanguageRepository languageRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private LanguageService languageService;

	private static final Long USER_ID = 1L;
	private static final String LANGUAGE_CODE = "ko";

	private User createUser() {
		return new User("test@example.com", "encodedPassword");
	}

	@Nested
	@DisplayName("언어 추가 (addLanguage)")
	class AddLanguageTest {

		@Test
		@DisplayName("성공: 중복 없고 10개 미만이면 언어를 추가한다")
		void 언어_추가_성공() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(languageRepository.existsByUserIdAndLanguageCodeAndType(USER_ID, LANGUAGE_CODE, LanguageType.NATIVE)).willReturn(false);
			given(languageRepository.countByUserId(USER_ID)).willReturn(2);
			given(languageRepository.save(any(Language.class))).willAnswer(inv -> inv.getArgument(0));

			// when
			Language result = languageService.addLanguage(USER_ID, LANGUAGE_CODE, LanguageType.NATIVE, LanguageLevel.NATIVE);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getLanguageCode()).isEqualTo(LANGUAGE_CODE);
			assertThat(result.getType()).isEqualTo(LanguageType.NATIVE);
		}

		@Test
		@DisplayName("실패: 사용자가 존재하지 않으면 ResourceNotFoundException 발생")
		void 사용자_없음_예외() {
			// given
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> languageService.addLanguage(USER_ID, LANGUAGE_CODE, LanguageType.NATIVE, LanguageLevel.NATIVE))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

			verify(languageRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 동일한 언어+타입이 이미 존재하면 DuplicateResourceException 발생")
		void 언어_중복_예외() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(languageRepository.existsByUserIdAndLanguageCodeAndType(USER_ID, LANGUAGE_CODE, LanguageType.NATIVE)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> languageService.addLanguage(USER_ID, LANGUAGE_CODE, LanguageType.NATIVE, LanguageLevel.NATIVE))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_LANGUAGE);
		}

		@Test
		@DisplayName("실패: 언어가 10개 이상이면 MaxLimitExceededException 발생")
		void 언어_최대_개수_초과_예외() {
			// given
			User user = createUser();
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(languageRepository.existsByUserIdAndLanguageCodeAndType(USER_ID, LANGUAGE_CODE, LanguageType.LEARNING)).willReturn(false);
			given(languageRepository.countByUserId(USER_ID)).willReturn(10);

			// when & then
			assertThatThrownBy(() -> languageService.addLanguage(USER_ID, LANGUAGE_CODE, LanguageType.LEARNING, LanguageLevel.BEGINNER))
				.isInstanceOf(MaxLimitExceededException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_LIMIT_EXCEEDED);
		}
	}

	@Nested
	@DisplayName("언어 삭제 (deleteLanguage)")
	class DeleteLanguageTest {

		@Test
		@DisplayName("성공: 자신의 언어를 삭제한다")
		void 언어_삭제_성공() {
			// given
			User user = createUser();
			Language language = Language.builder()
				.user(user)
				.languageCode(LANGUAGE_CODE)
				.type(LanguageType.NATIVE)
				.level(LanguageLevel.NATIVE)
				.build();
			given(languageRepository.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(language));

			// when
			languageService.deleteLanguage(USER_ID, 1L);

			// then
			verify(languageRepository).delete(language);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 언어 삭제 시 ResourceNotFoundException 발생")
		void 언어_없음_예외() {
			// given
			given(languageRepository.findByIdAndUserId(999L, USER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> languageService.deleteLanguage(USER_ID, 999L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.LANGUAGE_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("언어 전체 교체 (replaceLanguages)")
	class ReplaceLanguagesTest {

		@Test
		@DisplayName("성공: 기존 언어를 삭제하고 새로운 언어 목록으로 교체한다")
		void 언어_교체_성공() {
			// given
			User user = createUser();
			List<LanguageItem> items = List.of(
				new LanguageItem("ko", LanguageType.NATIVE, LanguageLevel.NATIVE),
				new LanguageItem("en", LanguageType.LEARNING, LanguageLevel.INTERMEDIATE)
			);
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(user));
			given(userRepository.getReferenceById(USER_ID)).willReturn(user);
			given(languageRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

			// when
			List<Language> result = languageService.replaceLanguages(USER_ID, items);

			// then
			assertThat(result).hasSize(2);
			verify(languageRepository).deleteByUserId(USER_ID);
		}

		@Test
		@DisplayName("실패: 중복 언어+타입 조합이 있으면 DuplicateResourceException 발생")
		void 중복_언어_교체_예외() {
			// given
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(createUser()));
			List<LanguageItem> items = List.of(
				new LanguageItem("ko", LanguageType.NATIVE, LanguageLevel.NATIVE),
				new LanguageItem("ko", LanguageType.NATIVE, LanguageLevel.ADVANCED)
			);

			// when & then
			assertThatThrownBy(() -> languageService.replaceLanguages(USER_ID, items))
				.isInstanceOf(DuplicateResourceException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_LANGUAGE);
		}

		@Test
		@DisplayName("실패: 11개 이상의 언어 교체 시 MaxLimitExceededException 발생")
		void 언어_11개_교체_예외() {
			// given
			given(userRepository.findActiveById(USER_ID)).willReturn(Optional.of(createUser()));
			List<LanguageItem> items = List.of(
				new LanguageItem("ko", LanguageType.NATIVE, LanguageLevel.NATIVE),
				new LanguageItem("en", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("ja", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("zh", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("es", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("fr", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("de", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("it", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("pt", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("ru", LanguageType.LEARNING, LanguageLevel.BEGINNER),
				new LanguageItem("ar", LanguageType.LEARNING, LanguageLevel.BEGINNER)
			);

			// when & then
			assertThatThrownBy(() -> languageService.replaceLanguages(USER_ID, items))
				.isInstanceOf(MaxLimitExceededException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_LIMIT_EXCEEDED);
		}
	}
}
