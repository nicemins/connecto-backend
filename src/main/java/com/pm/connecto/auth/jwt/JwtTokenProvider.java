package com.pm.connecto.auth.jwt;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private final SecretKey secretKey;
	private final long expirationTime;

	public JwtTokenProvider(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.expiration}") long expirationTime
	) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.expirationTime = expirationTime;
	}

	public String generateToken(Long userId) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + expirationTime);

		return Jwts.builder()
			.subject(String.valueOf(userId))
			.issuedAt(now)
			.expiration(expiryDate)
			.signWith(secretKey)
			.compact();
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public Long getUserIdFromToken(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(secretKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		return Long.parseLong(claims.getSubject());
	}
}
