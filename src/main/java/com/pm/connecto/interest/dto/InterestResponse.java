package com.pm.connecto.interest.dto;

import com.pm.connecto.interest.domain.Interest;

public record InterestResponse(
	Long id,
	String name
) {
	public static InterestResponse from(Interest interest) {
		return new InterestResponse(
			interest.getId(),
			interest.getName()
		);
	}
}
