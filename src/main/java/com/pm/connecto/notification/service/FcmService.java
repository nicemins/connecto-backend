package com.pm.connecto.notification.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.pm.connecto.notification.domain.DeviceToken;
import com.pm.connecto.notification.repository.DeviceTokenRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class FcmService {

	private static final Logger log = LoggerFactory.getLogger(FcmService.class);

	private final DeviceTokenRepository deviceTokenRepository;
	private final UserRepository userRepository;

	@Autowired(required = false)
	private FirebaseApp firebaseApp;

	public FcmService(
		DeviceTokenRepository deviceTokenRepository,
		UserRepository userRepository
	) {
		this.deviceTokenRepository = deviceTokenRepository;
		this.userRepository = userRepository;
	}

	// === 토큰 관리 ===

	@Transactional
	public void registerToken(Long userId, String token, String platform) {
		deviceTokenRepository.findByToken(token).ifPresentOrElse(
			existing -> { /* 이미 존재 — 무시 */ },
			() -> {
				User user = userRepository.getReferenceById(userId);
				deviceTokenRepository.save(new DeviceToken(user, token, platform));
				log.debug("FCM token registered for userId={}", userId);
			}
		);
	}

	@Transactional
	public void deleteToken(Long userId, String token) {
		deviceTokenRepository.deleteByUserIdAndToken(userId, token);
		log.debug("FCM token deleted for userId={}", userId);
	}

	@Transactional
	public void deleteAllTokens(Long userId) {
		deviceTokenRepository.deleteAllByUserId(userId);
		log.debug("All FCM tokens deleted for userId={}", userId);
	}

	// === 알림 전송 ===

	@Async
	@Transactional
	public void sendToUserAsync(Long userId, String title, String body) {
		if (firebaseApp == null) {
			log.debug("FCM not configured. Skipping notification to userId={}", userId);
			return;
		}

		List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
		if (tokens.isEmpty()) return;

		for (DeviceToken deviceToken : tokens) {
			sendMessage(deviceToken, title, body);
		}
	}

	// data payload 포함 FCM 전송 (백그라운드 딥링크용)
	@Async
	@Transactional
	public void sendToUserWithDataAsync(Long userId, String title, String body, Map<String, String> data) {
		if (firebaseApp == null) {
			log.debug("FCM not configured. Skipping data notification to userId={}", userId);
			return;
		}

		List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
		if (tokens.isEmpty()) return;

		for (DeviceToken deviceToken : tokens) {
			sendMessageWithData(deviceToken, title, body, data);
		}
	}

	private void sendMessage(DeviceToken deviceToken, String title, String body) {
		try {
			Message message = Message.builder()
				.setToken(deviceToken.getToken())
				.setNotification(Notification.builder()
					.setTitle(title)
					.setBody(body)
					.build())
				.build();
			FirebaseMessaging.getInstance(firebaseApp).send(message);
			log.debug("FCM sent to userId={}", deviceToken.getUser().getId());
		} catch (FirebaseMessagingException e) {
			if ("UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
				deviceTokenRepository.delete(deviceToken);
				log.info("Removed expired FCM token for userId={}", deviceToken.getUser().getId());
			} else {
				log.warn("FCM send failed for userId={}: {}", deviceToken.getUser().getId(), e.getMessage());
			}
		}
	}

	private void sendMessageWithData(DeviceToken deviceToken, String title, String body, Map<String, String> data) {
		try {
			Message.Builder builder = Message.builder()
				.setToken(deviceToken.getToken())
				.setNotification(Notification.builder()
					.setTitle(title)
					.setBody(body)
					.build());
			data.forEach(builder::putData);
			FirebaseMessaging.getInstance(firebaseApp).send(builder.build());
			log.debug("FCM with data sent to userId={}", deviceToken.getUser().getId());
		} catch (FirebaseMessagingException e) {
			if ("UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
				deviceTokenRepository.delete(deviceToken);
				log.info("Removed expired FCM token for userId={}", deviceToken.getUser().getId());
			} else {
				log.warn("FCM data send failed for userId={}: {}", deviceToken.getUser().getId(), e.getMessage());
			}
		}
	}
}
