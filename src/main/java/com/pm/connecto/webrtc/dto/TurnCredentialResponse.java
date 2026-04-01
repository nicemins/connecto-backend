package com.pm.connecto.webrtc.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TurnCredentialResponse(
	List<IceServer> iceServers,
	int ttl
) {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record IceServer(
		String urls,
		String username,
		String credential
	) {
		public static IceServer stun(String url) {
			return new IceServer(url, null, null);
		}

		public static IceServer turn(String url, String username, String credential) {
			return new IceServer(url, username, credential);
		}
	}
}
