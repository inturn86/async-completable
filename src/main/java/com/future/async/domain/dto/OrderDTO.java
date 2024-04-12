package com.future.async.domain.dto;

public record OrderDTO (
		String orderId,
		String orderStatusCd,
		String itemId,
		String address,
		Integer qty
){
}
