package sonyflake.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import sonyflake.config.SonyflakeSettings;

class SonyflakeTest {

    @Test
    void shouldGenerateId() throws Exception {
        // Given
        Instant startTime = Instant.parse("2021-05-17T00:00:00Z");
        SonyflakeSettings settings = SonyflakeSettings.of(startTime);
        Sonyflake sonyflake = Sonyflake.of(settings);

        // When
        long id = sonyflake.nextId();

        // Then
        assertTrue(id > 0, "ID should be greater than 0");
        System.out.println("Sonyflake.nextId=" + id);
        System.out.println("Sonyflake.startTime=" + startTime);
        System.out.println("Sonyflake.elapsedTime=" + sonyflake.elapsedTime(id));
        System.out.println("Sonyflake.sequenceNumber=" + sonyflake.sequenceNumber(id));
        System.out.println("Sonyflake.machineId=" + sonyflake.machineId(id));
        System.out.println("Sonyflake.timestamp=" + sonyflake.timestamp(id));
    }

    @Test
    void shouldGenerateOrderedIds() throws Exception {

        // Given
        SonyflakeSettings settings = SonyflakeSettings.of(Instant.parse("2025-01-01T00:00:00Z"));
        Sonyflake sonyflake = Sonyflake.of(settings);
        List<Long> ids = new ArrayList<>();
        int totalSize = 1024;

        // When
        for (int i = 0; i < totalSize; i++) {
            ids.add(sonyflake.nextId());
        }

        // Then
        for (int i = 1; i < totalSize; i++) {
            long prev = ids.get(i - 1);
            long next = ids.get(i);
            assertTrue(prev < next, "IDs should be ordered");
        }
    }

    @Test
    void shouldHandleHighTpsWithMultipleInstances() throws Exception {

        // Given
        int totalRequests = 50_000; // 50,000 TPS
        int threadCount = 10;       // 10 machines
        int maxElapsedTime = 1_000; // expected elapsed time in millis
        AtomicInteger idCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(totalRequests);

        List<Sonyflake> sonyflakeInstances = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            SonyflakeSettings settings = SonyflakeSettings.of(Instant.parse("2025-01-01T00:00:00Z"), i + 1);
            sonyflakeInstances.add(Sonyflake.of(settings));
        }

        // When
        long startTime = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < totalRequests; i++) {
                int threadIndex = i % threadCount;
                executor.submit(() -> {
                    try {
                        sonyflakeInstances.get(threadIndex).nextId();
                        idCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
        long endTime = System.nanoTime();

        // Then
        long actualElapsedTime = (endTime - startTime) / 1_000_000;
        assertTrue(actualElapsedTime < maxElapsedTime,
                "Expected: less than" + maxElapsedTime + ", Actual: " + actualElapsedTime + " ms");
        assertEquals(totalRequests, idCount.get(),
                "Expected: " + totalRequests + ", Actual: " + idCount.get());

        System.out.println("Total IDs generated: " + idCount.get());
        System.out.println("Time taken to generate IDs with multiple instances: " + actualElapsedTime + " ms");
    }
}