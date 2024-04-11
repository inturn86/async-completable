package com.future.async;

import com.future.async.domain.dto.OrderDTO;
import com.future.async.domain.dto.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(SpringExtension.class)
@Slf4j
class AsyncCompletableFutureTest {

	ExecutorService executorService = Executors.newCachedThreadPool();

	@DisplayName("주문 확정 및 푸시 메세지 전송")
	@Test
	void asyncOrderConfirm_Test() throws ExecutionException, InterruptedException {

		int orderCnt = 10;

		List<String> completeOrderList = new ArrayList<>();
		//order 목록 생성
		//주문 목록에 따른 confirm 처리
		for(OrderDTO order : createOrderList(orderCnt)) {
			CompletableFuture.supplyAsync(() -> confirmOrder(order))
			.thenAccept(orderId -> {
				System.out.println(Thread.currentThread().getName() + " Confirm Complele Order : OrderId = " + orderId);
				completeOrderList.add(orderId);
			});
		}

		//CompletableFuture block 은 위 비동기 처리를 기다
		CompletableFuture<Integer> block = CompletableFuture.supplyAsync(() -> finishSendOrder(orderCnt, completeOrderList));
		System.out.println(block.get());

		System.out.println("Thread: " + Thread.currentThread().getName());
	}

	private List<OrderDTO> createOrderList(int orderCnt) {
		List<OrderDTO> orderList = new ArrayList<>();
		for (int i = 0; i < orderCnt; i++) {
			String orderId = String.format("OD-%s", RandomStringUtils.randomNumeric(10));
			orderList.add(new OrderDTO(orderId, "READY", 1));
		}
		return orderList;
	}

	private String confirmOrder(OrderDTO order) {
		boolean confirm = false;
		AtomicInteger i = new AtomicInteger();
		while (!confirm) {
			i.getAndIncrement();
			var res = this.sendOrder(order, i);
			System.out.println(Thread.currentThread().getName() + " confirmOrder thread name sucess = " + res.getSuccess());
			if(ObjectUtils.isNotEmpty(res) && StringUtils.equals(order.orderId(), res.getOrderId())) confirm = true;

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return order.orderId();
	}

	/**
	 * order 데이터 전송 및 성공 여부 반환
	 * @param order
	 * @return
	 */
	private OrderResponse sendOrder(OrderDTO order, AtomicInteger i) {
		
//		boolean success = RandomUtils.nextBoolean();
		boolean success = false;
		if(i.get() > 1) {
			success = true;
		}
		return OrderResponse.builder().success(success).orderId(success ? order.orderId() : null).build();
	}

	private Integer finishSendOrder(int orderCnt, List<String> completeOrderList) {
		while(orderCnt != completeOrderList.size()){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return completeOrderList.size();
	}
}