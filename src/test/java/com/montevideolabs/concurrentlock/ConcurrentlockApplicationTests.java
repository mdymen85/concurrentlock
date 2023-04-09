package com.montevideolabs.concurrentlock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.stream.IntStream;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrentlockApplicationTests {

	@Autowired
	private SomeProcessor someProcessor;

    @Autowired
    private LockProviderImpl lockProvider;

	@Test
	void with_synchronized() throws InterruptedException {

		var limit = 2000;

		ExecutorService executor = newFixedThreadPool(1000);
		var event = new UserEvent("test");

		for (int i = 0; i < limit; i++) {
			executor.execute(() -> {
					someProcessor.process(event);
			});
		}

		executor.shutdown();

		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

		assertEquals(limit, event.getMoney());

	}

	@Test
	void without_synchronized() throws InterruptedException {

		var limit = 2000;

		ExecutorService executor = newFixedThreadPool(1000);
		var event = new UserEvent("test");

		for (int i = 0; i < limit; i++) {
			executor.execute(() -> {
				someProcessor.method(event);
			});
		}

		executor.shutdown();

		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

		assertNotEquals(limit, event.getMoney());

	}


    @Test
    public void testConcurrency() throws InterruptedException, ExecutionException {
        int numThreads = 10;
        int numIterationsPerThread = 1000;

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executorService);

        IntStream.range(0, numThreads).forEach(threadId -> {
            completionService.submit(() -> {
                IntStream.range(0, numIterationsPerThread).forEach(iterationId -> {
                    String lockId = "lock" + iterationId;
                    lockProvider.get(lockId);
                    if (iterationId % 2 == 0) {
                        lockProvider.get("lockToRemove");
                    }
                    if (iterationId % 5 == 0) {
                        lockProvider.get("lockToRemove2");
                    }
                    if (iterationId % 10 == 0) {
                        lockProvider.get("lockToRemove3");
                    }
                    if (iterationId % 20 == 0) {
                        lockProvider.get("lockToRemove4");
                    }
                });
                return null;
            });
        });

        for (int i = 0; i < numThreads; i++) {
            completionService.take().get();
        }

//        // Wait for locks cleanup
        Thread.sleep(10000);

        // Make sure all the locks have been removed
        assertNull(lockProvider.checkLock("lockToRemove"));
        assertNull(lockProvider.checkLock("lockToRemove2"));
        assertNull(lockProvider.checkLock("lockToRemove3"));
        assertNull(lockProvider.checkLock("lockToRemove4"));
    }

}
