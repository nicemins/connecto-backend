package com.pm.connecto.webrtc.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pm.connecto.webrtc.dto.TurnCredentialResponse;
import com.pm.connecto.webrtc.dto.TurnCredentialResponse.IceServer;

@Service
public class TurnCredentialService {

	private static final Logger log = LoggerFactory.getLogger(TurnCredentialService.class);
	private static final int TTL_SECONDS = 3600;

	@Value("${turn.secret:}")
	private String turnSecret;

	@Value("${turn.url:}")
	private String turnUrl;

	@Value("${turn.stun-url:stun:stun.l.google.com:19302}")
	private String stunUrl;

	public TurnCredentialResponse generateCredentials(Long userId) {
		List<IceServer> iceServers = new ArrayList<>();
		iceServers.add(IceServer.stun(stunUrl));

		if (!turnSecret.isBlank() && !turnUrl.isBlank()) {
			long expireTimestamp = System.currentTimeMillis() / 1000 + TTL_SECONDS;
			String username = expireTimestamp + ":" + userId;
			String credential = generateHmacSha1(turnSecret, username);
			iceServers.add(IceServer.turn(turnUrl, username, credential));
		}

		return new TurnCredentialResponse(iceServers, TTL_SECONDS);
	}

	private String generateHmacSha1(String secret, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
		} catch (Exception e) {
			log.error("TURN credential HMAC generation failed", e);
			throw new IllegalStateException("TURN credential generation failed", e);
		}
	}
}
