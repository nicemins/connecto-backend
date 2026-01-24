package com.pm.connecto.interest.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UserInterestRequest(
	@NotEmpty(message = "관심사 목록은 비어있을 수 없습니다.")
	@Size(max = 20, message = "관심사는 최대 20개까지 등록할 수 있습니다.")
	List<@Size(min = 1, max = 50, message = "관심사 이름은 1자 이상 50자 이하여야 합니다.") String> interestNames
) {
}
