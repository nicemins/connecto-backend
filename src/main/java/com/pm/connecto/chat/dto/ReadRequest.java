package com.pm.connecto.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ReadRequest(
	@NotNull Long lastMessageId
) {}
