package com.future.async.domain.dto;

public record OrderDTO (
		String orderId,
		String orderStatusCd,
		Integer qty
){
}
