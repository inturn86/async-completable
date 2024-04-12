package com.future.async.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class PushMessageResultDTO {

	private String orderId;

	private Boolean success;

	@Builder
	public PushMessageResultDTO(String orderId, Boolean success) {
		this.orderId = orderId;
		this.success = success;
	}
}
