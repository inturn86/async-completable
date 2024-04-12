package com.future.async;

import com.future.async.domain.dto.OrderDTO;
import com.future.async.domain.dto.OrderResponse;
import com.future.async.domain.dto.PushMessageResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scripting.support.ScriptFactoryPostProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ExtendWith(SpringExtension.class)
@Slf4j
class AsyncCompletableFutureTest {

	ExecutorService executorService = Executors.newCachedThreadPool();

	private final int orderCnt = 100;

	@DisplayName("CompletableFuture 주문 생성")
	@Test
	void completableFutureOrderCreate_Test() throws ExecutionException, InterruptedException {

		//order 목록 생성
		CompletableFuture.runAsync(() -> createOrderList(orderCnt));
		var future = CompletableFuture.runAsync(() -> createOrderList(orderCnt));

		//CompletableFuture는 ForkJoinPool.CommonPool()를 사용하는데 여기서 생성된 Thread는 데몬스레드이기에 확인을 위한 get처리
		//future가 없을 경우 설명
		future.get();
	}

	@DisplayName("CompletableFuture 주문 확정")
	@Test
	void completableFutureOrderConfirm() throws ExecutionException, InterruptedException {

		List<String> confirmOrderList = new ArrayList<>();
		//주문 목록에 따른 confirm 처리
		for(OrderDTO order : createOrderList(orderCnt)) {
			CompletableFuture.supplyAsync(() -> sendOrder(order))
					.thenAccept(res -> simpleConfirmOrder(confirmOrderList, res.getOrderId()));
		}

		//CompletableFuture block 은 위 비동기 처리를 기다
		CompletableFuture<Integer> block = CompletableFuture.supplyAsync(() -> finishConfirmOrder(orderCnt, confirmOrderList));
		System.out.println(block.get());
	}

	private String simpleConfirmOrder(List<String> confirmOrderList, String orderId) {
		log("Confirm Complele Order : OrderId = %s", orderId);
		confirmOrderList.add(orderId);
		return orderId;
	}

	private Integer finishConfirmOrder(int orderCnt, List<?> completeOrderList) {
		while(orderCnt != completeOrderList.size()){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return completeOrderList.size();
	}

	@DisplayName("CompletableFuture 주문 확정에 따른 Push Message 전송")
	@Test
	void completableFutureOrderConfirm_SendPushMessage() throws InterruptedException {

		List<PushMessageResultDTO> failPushMessageResultDTOList = new ArrayList<>();
		//주문 목록에 따른 confirm 처리
		for(OrderDTO order : createOrderList(orderCnt)) {
			CompletableFuture.supplyAsync(() -> confirmOrder(order))
					.thenApply(orderId -> sendPushMessageTask(orderId))
					.whenComplete((result, e) -> {
						if(!result.getSuccess())    failPushMessageResultDTOList.add(result);
					});
		}

		//CompleteFuture의 데몬 스레드를 유지하기 위한 테스트용 Sleep
		Thread.sleep(10000);
		//푸시 메세지 전송에 실패한 주문 목록.
		failPushMessageResultDTOList.stream().forEach(o -> System.out.println(o.getOrderId()));
	}

	private PushMessageResultDTO sendPushMessageTask(String orderId) {
		boolean result = false;
		AtomicInteger retry = new AtomicInteger();
		//푸시 메세지를 3회 전송에 대한 결과를 전송.
		while (!result) {
			retry.getAndIncrement();
			//푸시 메세지 전송 및 결과 반환
			result = sendPushMessage(orderId);
			log(String.format(" SendPushMessage thread name orderId = %s, result = %b", orderId, result));
			if(retry.get() >= 3) {
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return PushMessageResultDTO.builder().orderId(orderId).success(result).build();
	}

	/**
	 * orderId로 pushMessage 전송
	 * @param orderId
	 * @return
	 */
	private boolean sendPushMessage(String orderId) {
		boolean result = RandomUtils.nextBoolean();
		log(String.format("sendPushMessage result = %b", result));
		return result;

	}

	@DisplayName("CompletableFuture Compose 주문 생성 및 주문 확정")
	@Test
	void completableFuture_ComposeCreateOrderAndConfirmOrder() throws ExecutionException, InterruptedException, TimeoutException {

		var future = CompletableFuture.supplyAsync(() -> createOrder())
				.thenCompose(order -> CompletableFuture.supplyAsync(() -> confirmOrder(order)));

		log(future.get());
	}

	/**
	 * order 데이터 전송 및 성공 여부 반환
	 * @param order
	 * @return
	 */
	private OrderResponse sendOrder(OrderDTO order) {
		boolean success = true;
		return OrderResponse.builder().success(success).orderId(success ? order.orderId() : null).build();
	}

	private String confirmOrder(OrderDTO order) {
		var res = this.sendOrder(order);
		log(" confirmOrder thread name success = %s", res.getSuccess().toString());
		//성공 여부에 따른 confirm 처리.
		if(ObjectUtils.isNotEmpty(res) && StringUtils.equals(order.orderId(), res.getOrderId())) {
			return res.getOrderId();
		};
		return StringUtils.EMPTY;
	}

	@DisplayName("CompletableFuture 주문 및 배송지 조회")
	@Test
	void completableFutureOrderConfirm_CombineOrderAndShippingInfo() throws ExecutionException, InterruptedException, TimeoutException {

		OrderDTO order = createOrder();
		//주문 목록에 따른 confirm 처리
		var future = CompletableFuture.supplyAsync(() -> getItemInfo(order.itemId()))
					.thenCombine(CompletableFuture.supplyAsync(() -> getShippingInfo(order.address())), (s1, s2) -> completeOrder(s1, s2));

		log(future.get());
	}


	private OrderDTO createOrder() {
		String orderId = String.format("OD-%d", RandomUtils.nextInt());
		log("Create Order = %s", orderId);
		return new OrderDTO(orderId, "READY", "ITEM", "ADDRESS", 1);
	}

	private String getItemInfo(String itemId) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		log("getItemInfo = %s ", itemId);
		return itemId;
	}

	private String getShippingInfo(String address) {
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		log("getShippingInfo = %s ", address);
		return address;
	}

	private String completeOrder(String itemId, String address) {
		String completeOrder = String.format("item = %s, address = %s", itemId, address);
		log(completeOrder);
		return completeOrder;
	}


	@DisplayName("CompletableFuture 주문 목록 일괄 확정")
	@Test
	void completableFuture_AllOfOrderConfirm() throws ExecutionException, InterruptedException, TimeoutException {

		final int confirmTime = 100;

		List<CompletableFuture<String>> futureList = new ArrayList<>();
		//주문 목록에 따른 confirm 처리
		for(OrderDTO order : createOrderList(orderCnt)) {
			CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> confirmOrder(order.orderId(), confirmTime));
			futureList.add(future);
		}
		CompletableFuture<String>[] futureArr = new CompletableFuture[orderCnt];
		CompletableFuture<List<String>> allOfFuture = CompletableFuture.allOf(futureList.toArray(futureArr))
				.thenApply(o -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList())
		);
		System.out.println(allOfFuture.get());
	}

	@DisplayName("CompletableFuture 주문 목록 빠른 확정")
	@Test
	void completableFuture_AnyOfOrderConfirm() throws ExecutionException, InterruptedException, TimeoutException {

		final int confirmTime = 100;

		List<CompletableFuture<String>> futureList = new ArrayList<>();
		//주문 목록에 따른 confirm 처리
		for(OrderDTO order : createOrderList(orderCnt)) {
			CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> confirmOrder(order.orderId(), confirmTime));
			futureList.add(future);
		}
		CompletableFuture<String>[] futureArr = new CompletableFuture[orderCnt];
		CompletableFuture<Void> anyOfFuture = CompletableFuture.anyOf(futureList.toArray(futureArr))
				.thenAccept(o -> System.out.println(o));
		anyOfFuture.get();
	}

	private String confirmOrder(String orderId, int confirmTime) {
		try {
			Thread.sleep(confirmTime);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		log(" confirmOrder orderId = %s", orderId);
		return orderId;
	}

	@DisplayName("CompletableFuture 에러 헨들링")
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void completableFuture_ErrorHandle(boolean param) throws ExecutionException, InterruptedException{

		var future = CompletableFuture.supplyAsync(() -> createOrder()).thenApply(order -> {
			if(param) {
				throw new IllegalArgumentException("throw Illegal Exception");
			}
			return order.orderId();
		}).handle((result, e) -> e == null ? result : e.toString());
		log(future.get());
	}

	@DisplayName("CompletableFuture 에러 헨들링")
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void completableFuture_ErrorExceptionally(boolean param) throws ExecutionException, InterruptedException{

		var future = CompletableFuture.supplyAsync(() -> createOrder()).thenApply(order -> {
			if(param) {
				throw new IllegalArgumentException("throw Illegal Exception");
			}
			return order.orderId();
		}).exceptionally(e -> e.toString());
		log(future.get());
	}

	private List<OrderDTO> createOrderList(int orderCnt) {
		List<OrderDTO> orderList = new ArrayList<>();
		for (int i = 0; i < orderCnt; i++) {
			String orderId = String.format("OD-%d", i);
			orderList.add(new OrderDTO(orderId, "READY"
					, String.format("ITEM-%s", i), String.format("addr %s", i),1));
			log("Create Order = %s", orderId);
		}
		return orderList;
	}


	private void log(String comment, String... args) {
		System.out.println(String.format("[%s - %s] %s", LocalDateTime.now(), Thread.currentThread().getName(), String.format(comment, args)));
	}

	private void log(String comment) {
		System.out.println(String.format("[%s - %s] %s", LocalDateTime.now(), Thread.currentThread().getName(), comment));
	}
}