package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.TestcontainersConfiguration;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.StoreEntrance;
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
class StoreEntranceRepositoryIT {

    private static final String COURIER = "courier-1";
    private static final String STORE = "Ortaköy MMM Migros";
    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");
    private static final GeoPoint POINT = new GeoPoint(41.0558, 29.0210);

    @Autowired
    private StoreEntranceRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void it_should_return_latest_entrance_for_courier_store_pair() {
        // Given
        repository.save(new StoreEntrance(COURIER, STORE, T0.plusSeconds(120), POINT));
        repository.save(new StoreEntrance(COURIER, STORE, T0, POINT));
        repository.save(new StoreEntrance(COURIER, STORE, T0.plusSeconds(60), POINT));
        repository.save(new StoreEntrance("courier-2", STORE, T0.plusSeconds(999), POINT));
        repository.save(new StoreEntrance(COURIER, "Ataşehir MMM Migros", T0.plusSeconds(999),
                new GeoPoint(40.9923, 29.1244)));
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<StoreEntrance> latest =
                repository.findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(COURIER, STORE);

        // Then
        assertTrue(latest.isPresent());
        assertEquals(T0.plusSeconds(120), latest.get().getEntranceTime());
    }

    @Test
    void it_should_return_empty_when_pair_has_no_entrances() {
        // Given
        repository.save(new StoreEntrance("courier-2", STORE, T0, POINT));
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<StoreEntrance> latest =
                repository.findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(COURIER, STORE);

        // Then
        assertTrue(latest.isEmpty());
    }
}
