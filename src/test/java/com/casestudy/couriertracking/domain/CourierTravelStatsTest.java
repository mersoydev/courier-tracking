package com.casestudy.couriertracking.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourierTravelStatsTest {

    private static final String COURIER = "courier-1";
    private static final GeoPoint P0 = new GeoPoint(41.05, 29.02);
    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");

    private CourierTravelStats stats() {
        return new CourierTravelStats(COURIER, P0, T0);
    }

    @Test
    void it_should_start_with_zero_distance() {
        // When
        CourierTravelStats stats = stats();

        // Then
        assertEquals(0.0, stats.getTotalDistanceMeters(), 1e-9);
        assertEquals(P0, stats.getLastPoint());
        assertEquals(T0, stats.getLastEventTime());
        assertEquals(COURIER, stats.getCourierId());
    }

    @Test
    void it_should_accumulate_non_negative_distance() {
        // Given
        CourierTravelStats stats = stats();

        // When
        stats.addDistance(100.5);
        stats.addDistance(50.0);

        // Then
        assertEquals(150.5, stats.getTotalDistanceMeters(), 1e-9);
    }

    @Test
    void it_should_reject_negative_distance() {
        // Given
        CourierTravelStats stats = stats();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> stats.addDistance(-0.001));
    }

    @Test
    void it_should_reject_nan_distance() {
        // Given
        CourierTravelStats stats = stats();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> stats.addDistance(Double.NaN));
    }

    @Test
    void it_should_reject_infinite_distance() {
        // Given
        CourierTravelStats stats = stats();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> stats.addDistance(Double.POSITIVE_INFINITY));
    }

    @Test
    void it_should_record_a_forward_point() {
        // Given
        CourierTravelStats stats = stats();
        GeoPoint next = new GeoPoint(41.06, 29.03);
        Instant later = T0.plusSeconds(30);

        // When
        stats.recordPoint(next, later);

        // Then
        assertEquals(next, stats.getLastPoint());
        assertEquals(later, stats.getLastEventTime());
    }

    @Test
    void it_should_accept_a_point_with_equal_time() {
        // Given
        CourierTravelStats stats = stats();
        GeoPoint next = new GeoPoint(41.06, 29.03);

        // When
        stats.recordPoint(next, T0);

        // Then
        assertEquals(next, stats.getLastPoint());
        assertEquals(T0, stats.getLastEventTime());
    }

    @Test
    void it_should_reject_a_point_going_backwards_in_time() {
        // Given
        CourierTravelStats stats = stats();
        GeoPoint point = new GeoPoint(41.06, 29.03);
        Instant earlier = T0.minusSeconds(1);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> stats.recordPoint(point, earlier));
    }
}
