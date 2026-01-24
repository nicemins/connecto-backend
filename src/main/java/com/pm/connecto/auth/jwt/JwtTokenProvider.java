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
	private final long accessExpiration;
	private final long refreshExpiration;

	public JwtTokenProvider(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.access-expiration}") long accessExpiration,
		@Value("${jwt.refresh-expiration}") long refreshExpiration
	) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.accessExpiration = accessExpiration;
		this.refreshExpiration = refreshExpiration;
	}

	public String generateAccessToken(Long userId) {
		return generateToken(userId, accessExpiration);
	}

	public String generateRefreshToken(Long userId) {
		return generateToken(userId, refreshExpiration);
	}

	private String generateToken(Long userId, long expiration) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + expiration);

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

	public long getRefreshExpiration() {
		return refreshExpiration;
	}
}
