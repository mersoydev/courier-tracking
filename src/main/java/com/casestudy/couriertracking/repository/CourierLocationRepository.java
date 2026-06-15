package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.CourierLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CourierLocationRepository extends JpaRepository<CourierLocation, Long> {

    boolean existsByCourierId(String courierId);

    boolean existsByCourierIdAndEventTimeAndLatAndLng(
            String courierId, Instant eventTime, double lat, double lng);

    List<CourierLocation> findByCourierIdOrderByIdAsc(String courierId);
}
