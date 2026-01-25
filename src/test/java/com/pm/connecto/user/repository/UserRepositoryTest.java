package com.pm.connecto.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.domain.UserStatus;

@DataJpaTest
@DisplayName("UserRepository 테스트")
class UserRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_PASSWORD = "encodedPassword123";

	@Nested
	@DisplayName("findByEmailForAuth 커스텀 쿼리 테스트")
	class FindByEmailForAuthTest {

		@Test
		@DisplayName("성공: 이메일로 활성 사용자를 조회할 수 있다")
		void 활성_사용자_이메일_조회_성공() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findByEmailForAuth(TEST_EMAIL);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getEmail()).isEqualTo(TEST_EMAIL);
			assertThat(result.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
		}

		@Test
		@DisplayName("성공: 이메일로 차단된 사용자도 조회할 수 있다 (상태 무관)")
		void 차단_사용자_이메일_조회_성공() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.block();
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findByEmailForAuth(TEST_EMAIL);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getEmail()).isEqualTo(TEST_EMAIL);
			assertThat(result.get().getStatus()).isEqualTo(UserStatus.BLOCKED);
		}

		@Test
		@DisplayName("성공: 이메일로 탈퇴한 사용자도 조회할 수 있다 (상태 무관)")
		void 탈퇴_사용자_이메일_조회_성공() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.delete();
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findByEmailForAuth(TEST_EMAIL);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getEmail()).isEqualTo(TEST_EMAIL);
			assertThat(result.get().getStatus()).isEqualTo(UserStatus.DELETED);
			assertThat(result.get().getDeletedAt()).isNotNull();
		}

		@Test
		@DisplayName("실패: 존재하지 않는 이메일로 조회하면 빈 Optional 반환")
		void 존재하지_않는_이메일_조회() {
			// when
			Optional<User> result = userRepository.findByEmailForAuth("nonexistent@example.com");

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("findByIdForAuth 커스텀 쿼리 테스트")
	class FindByIdForAuthTest {

		@Test
		@DisplayName("성공: ID로 모든 상태의 사용자를 조회할 수 있다")
		void ID로_사용자_조회_성공() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			User savedUser = entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findByIdForAuth(savedUser.getId());

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getId()).isEqualTo(savedUser.getId());
		}
	}

	@Nested
	@DisplayName("findActiveById 테스트 (ACTIVE 상태만)")
	class FindActiveByIdTest {

		@Test
		@DisplayName("성공: ACTIVE 상태 사용자만 조회된다")
		void 활성_사용자만_조회() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			User savedUser = entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findActiveById(savedUser.getId());

			// then
			assertThat(result).isPresent();
			assertThat(result.get().isActive()).isTrue();
		}

		@Test
		@DisplayName("실패: BLOCKED 상태 사용자는 조회되지 않는다")
		void 차단_사용자_조회_불가() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.block();
			User savedUser = entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findActiveById(savedUser.getId());

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("실패: DELETED 상태 사용자는 조회되지 않는다")
		void 탈퇴_사용자_조회_불가() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.delete();
			User savedUser = entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			Optional<User> result = userRepository.findActiveById(savedUser.getId());

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("existsByEmail 테스트 (DELETED 제외)")
	class ExistsByEmailTest {

		@Test
		@DisplayName("성공: ACTIVE 상태 사용자 이메일은 중복으로 판정")
		void 활성_사용자_이메일_중복_확인() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			boolean result = userRepository.existsByEmail(TEST_EMAIL);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("성공: BLOCKED 상태 사용자 이메일도 중복으로 판정")
		void 차단_사용자_이메일_중복_확인() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.block();
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			boolean result = userRepository.existsByEmail(TEST_EMAIL);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("성공: DELETED 상태 사용자 이메일은 중복 아님 (재사용 가능)")
		void 탈퇴_사용자_이메일_재사용_가능() {
			// given
			User user = new User(TEST_EMAIL, TEST_PASSWORD);
			user.delete();
			entityManager.persistAndFlush(user);
			entityManager.clear();

			// when
			boolean result = userRepository.existsByEmail(TEST_EMAIL);

			// then
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("성공: 존재하지 않는 이메일은 중복 아님")
		void 존재하지_않는_이메일_중복_아님() {
			// when
			boolean result = userRepository.existsByEmail("nonexistent@example.com");

			// then
			assertThat(result).isFalse();
		}
	}
}
