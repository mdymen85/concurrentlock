package com.montevideolabs.concurrentlock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class ConcurrentlockApplicationTests {

	@Autowired
	private SomeProcessor someProcessor;

	@Test
	void with_synchronized() throws InterruptedException {

		var limit = 2000;

		ExecutorService executor = Executors.newFixedThreadPool(1000);
		var event = new UserEvent("test");

		for (int i = 0; i < limit; i++) {
			executor.execute(() -> {
					someProcessor.process(event);
			});
		}

		executor.shutdown();

		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

		assertEquals(event.getMoney(), limit);

	}

	@Test
	void without_synchronized() throws InterruptedException {

		var limit = 2000;

		ExecutorService executor = Executors.newFixedThreadPool(1000);
		var event = new UserEvent("test");

		for (int i = 0; i < limit; i++) {
			executor.execute(() -> {
				someProcessor.method(event);
			});
		}

		executor.shutdown();

		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

		assertNotEquals(event.getMoney(), limit);

	}

}
