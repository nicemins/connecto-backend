package com.pm.connecto.webrtc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.pm.connecto.webrtc.dto.TurnCredentialResponse;

@DisplayName("TurnCredentialService 단위 테스트")
class TurnCredentialServiceTest {

	private TurnCredentialService service;

	@BeforeEach
	void setUp() {
		service = new TurnCredentialService();
	}

	@Nested
	@DisplayName("TURN 미설정 환경 (로컬 개발)")
	class WhenTurnNotConfigured {

		@BeforeEach
		void setUp() {
			ReflectionTestUtils.setField(service, "turnSecret", "");
			ReflectionTestUtils.setField(service, "turnUrl", "");
			ReflectionTestUtils.setField(service, "stunUrl", "stun:stun.l.google.com:19302");
		}

		@Test
		@DisplayName("STUN 서버만 포함된 자격증명을 반환한다")
		void returnsStunOnly() {
			TurnCredentialResponse response = service.generateCredentials(1L);

			assertThat(response.iceServers()).hasSize(1);
			assertThat(response.iceServers().get(0).urls()).isEqualTo("stun:stun.l.google.com:19302");
			assertThat(response.iceServers().get(0).username()).isNull();
			assertThat(response.iceServers().get(0).credential()).isNull();
		}

		@Test
		@DisplayName("TTL은 3600초이다")
		void ttlIs3600() {
			TurnCredentialResponse response = service.generateCredentials(1L);

			assertThat(response.ttl()).isEqualTo(3600);
		}
	}

	@Nested
	@DisplayName("TURN 설정된 환경")
	class WhenTurnConfigured {

		private static final String TEST_SECRET = "test-turn-secret";
		private static final String TEST_TURN_URL = "turn:test.example.com:3478";

		@BeforeEach
		void setUp() {
			ReflectionTestUtils.setField(service, "turnSecret", TEST_SECRET);
			ReflectionTestUtils.setField(service, "turnUrl", TEST_TURN_URL);
			ReflectionTestUtils.setField(service, "stunUrl", "stun:stun.l.google.com:19302");
		}

		@Test
		@DisplayName("STUN + TURN 두 서버를 반환한다")
		void returnsBothStunAndTurn() {
			TurnCredentialResponse response = service.generateCredentials(42L);

			assertThat(response.iceServers()).hasSize(2);
			assertThat(response.iceServers().get(0).urls()).startsWith("stun:");
			assertThat(response.iceServers().get(1).urls()).isEqualTo(TEST_TURN_URL);
		}

		@Test
		@DisplayName("username 형식은 {timestamp}:{userId}이다")
		void usernameFormatIsTimestampColonUserId() {
			TurnCredentialResponse response = service.generateCredentials(42L);

			String username = response.iceServers().get(1).username();
			assertThat(username).matches("\\d+:42");
		}

		@Test
		@DisplayName("username의 timestamp는 현재 시각 + 3600초이다")
		void usernameTimestampIsExpireTime() {
			long before = System.currentTimeMillis() / 1000 + 3600;
			TurnCredentialResponse response = service.generateCredentials(1L);
			long after = System.currentTimeMillis() / 1000 + 3600;

			String username = response.iceServers().get(1).username();
			long timestamp = Long.parseLong(username.split(":")[0]);

			assertThat(timestamp).isBetween(before, after);
		}

		@Test
		@DisplayName("credential은 HMAC-SHA1(secret, username)의 Base64 인코딩이다")
		void credentialIsHmacSha1OfSecretAndUsername() throws Exception {
			TurnCredentialResponse response = service.generateCredentials(42L);

			String username = response.iceServers().get(1).username();
			String credential = response.iceServers().get(1).credential();

			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(TEST_SECRET.getBytes(), "HmacSHA1"));
			String expected = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes()));

			assertThat(credential).isEqualTo(expected);
		}

		@Test
		@DisplayName("서로 다른 userId는 서로 다른 username을 가진다")
		void differentUsersHaveDifferentUsernames() {
			TurnCredentialResponse response1 = service.generateCredentials(1L);
			TurnCredentialResponse response2 = service.generateCredentials(2L);

			String username1 = response1.iceServers().get(1).username();
			String username2 = response2.iceServers().get(1).username();

			assertThat(username1).endsWith(":1");
			assertThat(username2).endsWith(":2");
		}
	}

	@Nested
	@DisplayName("TURN secret만 설정되고 URL이 비어있는 경우")
	class WhenTurnSecretSetButUrlEmpty {

		@BeforeEach
		void setUp() {
			ReflectionTestUtils.setField(service, "turnSecret", "some-secret");
			ReflectionTestUtils.setField(service, "turnUrl", "");
			ReflectionTestUtils.setField(service, "stunUrl", "stun:stun.l.google.com:19302");
		}

		@Test
		@DisplayName("STUN only를 반환한다 (URL 없으면 TURN 비활성)")
		void returnsStunOnlyWhenUrlMissing() {
			TurnCredentialResponse response = service.generateCredentials(1L);

			assertThat(response.iceServers()).hasSize(1);
			assertThat(response.iceServers().get(0).urls()).startsWith("stun:");
		}
	}
}
