package com.casestudy.couriertracking.service;

import com.casestudy.couriertracking.TestcontainersConfiguration;
import com.casestudy.couriertracking.distance.DistanceCalculator;
import com.casestudy.couriertracking.domain.CourierLocation;
import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.repository.CourierLocationRepository;
import com.casestudy.couriertracking.repository.CourierTravelStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CourierTrackingServiceConcurrencyIT {

    private static final double FAR_LAT = 40.5;
    private static final double FAR_LNG = 30.5;
    private static final int THREADS = 20;

    @Autowired
    private CourierTrackingService trackingService;

    @Autowired
    private CourierTravelStatsRepository statsRepository;

    @Autowired
    private CourierLocationRepository locationRepository;

    @Autowired
    private DistanceCalculator distanceCalculator;

    private void runConcurrently(Runnable perThread) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CompletableFuture<?>[] futures = new CompletableFuture<?>[THREADS];
        for (int i = 0; i < THREADS; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    startGate.await();
                    perThread.run();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }, pool);
        }
        startGate.countDown();
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @Test
    void it_should_handle_concurrent_first_points_without_loss_or_corruption() throws Exception {
        // Given/When
        String courier = "race-courier";
        Instant time = Instant.now().minusSeconds(60);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        runConcurrently(() -> {
            try {
                trackingService.recordLocation(courier, time, FAR_LAT, FAR_LNG);
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        });

        // Then
        assertTrue(failures.isEmpty(), () -> "Eşzamanlı ilk-noktalar hata aldı: " + failures);
        Optional<CourierTravelStats> stats = statsRepository.findById(courier);
        assertTrue(stats.isPresent());
        assertEquals(0.0, stats.get().getTotalDistanceMeters(), 1e-9);
    }

    @Test
    void it_should_not_lose_updates_under_concurrent_accumulation() throws Exception {
        // Given/When
        String courier = "lost-update-courier";
        Instant time = Instant.now().minusSeconds(60);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger index = new AtomicInteger();

        runConcurrently(() -> {
            int threadIndex = index.getAndIncrement();
            double lat = FAR_LAT + threadIndex * 0.0005;
            try {
                trackingService.recordLocation(courier, time, lat, FAR_LNG);
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        });

        // Then
        assertTrue(failures.isEmpty(), () -> "Eşzamanlı noktalar hata aldı: " + failures);
        List<CourierLocation> accepted = locationRepository.findByCourierIdOrderByIdAsc(courier);
        assertEquals(THREADS, accepted.size());

        double expected = 0.0;
        for (int i = 1; i < accepted.size(); i++) {
            expected += distanceCalculator.calculateInMeters(
                    new GeoPoint(accepted.get(i - 1).getLat(), accepted.get(i - 1).getLng()),
                    new GeoPoint(accepted.get(i).getLat(), accepted.get(i).getLng()));
        }
        double actual = statsRepository.findById(courier).orElseThrow().getTotalDistanceMeters();
        assertEquals(expected, actual, 1e-6);
    }
}
