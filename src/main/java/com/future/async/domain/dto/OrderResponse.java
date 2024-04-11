package com.future.async.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class OrderResponse {

	private final Boolean success;

	private final String errorCode;

	private final String orderId;

	@Builder
	public OrderResponse(Boolean success, String errorCode, String orderId) {
		this.success = success;
		this.errorCode = errorCode;
		this.orderId = orderId;
	}
}
