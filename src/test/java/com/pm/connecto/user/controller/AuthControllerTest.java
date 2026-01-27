package com.pm.connecto.user.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.LoginRequest;
import com.pm.connecto.user.dto.UserCreateRequest;
import com.pm.connecto.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AuthController 통합 테스트")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_PASSWORD = "password123";

	@Nested
	@DisplayName("POST /auth/signup - 회원가입")
	class SignupTest {

		@Test
		@DisplayName("성공: 유효한 정보로 회원가입하면 201 Created 반환")
		void 회원가입_성공() throws Exception {
			// given
			UserCreateRequest request = new UserCreateRequest(TEST_EMAIL, TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.email", is(TEST_EMAIL)))
				.andExpect(jsonPath("$.data.id", notNullValue()));
		}

		@Test
		@DisplayName("실패: 중복 이메일로 회원가입하면 409 Conflict 반환")
		void 중복_이메일_회원가입_실패() throws Exception {
			// given
			User existingUser = new User(TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD));
			userRepository.save(existingUser);

			UserCreateRequest request = new UserCreateRequest(TEST_EMAIL, TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("DUPLICATE_EMAIL")));
		}

		@Test
		@DisplayName("실패: 이메일 형식이 잘못되면 400 Bad Request 반환")
		void 잘못된_이메일_형식_회원가입_실패() throws Exception {
			// given
			UserCreateRequest request = new UserCreateRequest("invalid-email", TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("INVALID_INPUT")));
		}

		@Test
		@DisplayName("실패: 비밀번호가 너무 짧으면 400 Bad Request 반환")
		void 짧은_비밀번호_회원가입_실패() throws Exception {
			// given
			UserCreateRequest request = new UserCreateRequest(TEST_EMAIL, "short");

			// when
			ResultActions result = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("INVALID_INPUT")));
		}

		@Test
		@DisplayName("실패: 이메일이 비어있으면 400 Bad Request 반환")
		void 빈_이메일_회원가입_실패() throws Exception {
			// given
			UserCreateRequest request = new UserCreateRequest("", TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success", is(false)));
		}
	}

	@Nested
	@DisplayName("POST /auth/login - 로그인")
	class LoginTest {

		@BeforeEach
		void setUp() {
			// 테스트용 사용자 생성
			User user = new User(TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD));
			userRepository.save(user);
		}

		@Test
		@DisplayName("성공: 올바른 정보로 로그인하면 200 OK와 Access Token 반환")
		void 로그인_성공() throws Exception {
			// given
			LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.accessToken", notNullValue()))
				.andExpect(cookie().exists("refreshToken"))
				.andExpect(cookie().httpOnly("refreshToken", true));
		}

		@Test
		@DisplayName("실패: 존재하지 않는 이메일로 로그인하면 404 Not Found 반환")
		void 존재하지_않는_이메일_로그인_실패() throws Exception {
			// given
			LoginRequest request = new LoginRequest("nonexistent@example.com", TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("USER_NOT_FOUND")));
		}

		@Test
		@DisplayName("실패: 잘못된 비밀번호로 로그인하면 401 Unauthorized 반환")
		void 잘못된_비밀번호_로그인_실패() throws Exception {
			// given
			LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongPassword");

			// when
			ResultActions result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("INVALID_PASSWORD")));
		}

		@Test
		@DisplayName("실패: 탈퇴한 사용자가 로그인하면 401 Unauthorized 반환")
		void 탈퇴_사용자_로그인_실패() throws Exception {
			// given
			User user = userRepository.findByEmailForAuth(TEST_EMAIL).orElseThrow();
			user.delete();
			userRepository.save(user);

			LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("DELETED_USER")));
		}

		@Test
		@DisplayName("실패: 차단된 사용자가 로그인하면 403 Forbidden 반환")
		void 차단_사용자_로그인_실패() throws Exception {
			// given
			User user = userRepository.findByEmailForAuth(TEST_EMAIL).orElseThrow();
			user.block();
			userRepository.save(user);

			LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

			// when
			ResultActions result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

			// then
			result.andDo(print())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.code", is("BLOCKED_USER")));
		}
	}

	@Nested
	@DisplayName("POST /auth/logout - 로그아웃")
	class LogoutTest {

		@Test
		@DisplayName("성공: 로그아웃하면 204 No Content와 쿠키 삭제")
		void 로그아웃_성공() throws Exception {
			// when
			ResultActions result = mockMvc.perform(post("/auth/logout"));

			// then
			result.andDo(print())
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge("refreshToken", 0));
		}
	}
}
