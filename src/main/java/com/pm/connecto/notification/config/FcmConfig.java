package com.pm.connecto.notification.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FcmConfig {

	private static final Logger log = LoggerFactory.getLogger(FcmConfig.class);

	@Value("${firebase.service-account-json:}")
	private String serviceAccountJson;

	/**
	 * Firebase App 초기화.
	 * FIREBASE_SERVICE_ACCOUNT_JSON 환경변수가 없으면 null 반환 (FCM 비활성).
	 */
	@Bean
	public FirebaseApp firebaseApp() {
		if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
			log.info("Firebase service account not configured. Push notifications disabled.");
			return null;
		}

		try {
			if (!FirebaseApp.getApps().isEmpty()) {
				return FirebaseApp.getInstance();
			}

			InputStream stream = new ByteArrayInputStream(
				serviceAccountJson.getBytes(StandardCharsets.UTF_8)
			);
			FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.fromStream(stream))
				.build();

			FirebaseApp app = FirebaseApp.initializeApp(options);
			log.info("Firebase initialized successfully.");
			return app;

		} catch (Exception e) {
			log.warn("Firebase initialization failed: {}. Push notifications disabled.", e.getMessage());
			return null;
		}
	}
}
