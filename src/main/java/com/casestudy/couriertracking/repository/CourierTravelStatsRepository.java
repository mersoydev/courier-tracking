package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.CourierTravelStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourierTravelStatsRepository extends JpaRepository<CourierTravelStats, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CourierTravelStats> findWithLockingByCourierId(String courierId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO courier_travel_stats (courier_id, total_distance_meters, last_lat, last_lng, last_event_time)
            VALUES (:courierId, 0, 0, 0, '1000-01-01 00:00:00')
            ON DUPLICATE KEY UPDATE courier_id = courier_id
            """, nativeQuery = true)
    void ensureRow(@Param("courierId") String courierId);
}
