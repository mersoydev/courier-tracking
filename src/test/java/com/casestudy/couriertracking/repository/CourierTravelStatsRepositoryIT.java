package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.TestcontainersConfiguration;
import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CourierTravelStatsRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");
    private static final GeoPoint POINT = new GeoPoint(41.05, 29.02);

    @Autowired
    private CourierTravelStatsRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void it_should_persist_and_read_back_a_stats_row() {
        // Given
        repository.save(new CourierTravelStats("courier-1", POINT, T0));
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<CourierTravelStats> found = repository.findById("courier-1");

        // Then
        assertTrue(found.isPresent());
        assertEquals(0.0, found.get().getTotalDistanceMeters(), 1e-9);
        assertEquals(POINT, found.get().getLastPoint());
        assertEquals(T0, found.get().getLastEventTime());
    }

    @Test
    void it_should_return_locked_row_for_existing_courier() {
        // Given
        repository.save(new CourierTravelStats("courier-1", POINT, T0));
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<CourierTravelStats> locked =
                repository.findWithLockingByCourierId("courier-1");

        // Then
        assertTrue(locked.isPresent());
        assertEquals("courier-1", locked.get().getCourierId());
    }

    @Test
    void it_should_return_empty_locking_query_for_unknown_courier() {
        // When
        Optional<CourierTravelStats> locked =
                repository.findWithLockingByCourierId("ghost");

        // Then
        assertTrue(locked.isEmpty());
    }

    @Test
    void it_should_distinguish_courier_ids_by_binary_collation() {
        // Given
        repository.save(new CourierTravelStats("ali", POINT, T0));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertTrue(repository.findById("ALI").isEmpty());
        assertTrue(repository.findById("ALİ").isEmpty());
        assertTrue(repository.findById("ali").isPresent());
    }
}
