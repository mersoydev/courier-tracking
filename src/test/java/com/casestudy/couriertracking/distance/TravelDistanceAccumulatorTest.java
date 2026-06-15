package com.casestudy.couriertracking.distance;

import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdate.PreviousPoint;
import com.casestudy.couriertracking.repository.CourierTravelStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelDistanceAccumulatorTest {

    private static final String COURIER = "courier-1";
    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(20);

    private static final GeoPoint P0 = new GeoPoint(41.055783, 29.0210292);
    private static final GeoPoint P1 = new GeoPoint(P0.lat() + 0.0009, P0.lng());
    private static final GeoPoint P2 = new GeoPoint(P0.lat() + 0.0018, P0.lng());
    private static final double STEP_METERS = 100.0754;

    @Mock
    private CourierTravelStatsRepository statsRepository;

    private TravelDistanceAccumulator observer() {
        return new TravelDistanceAccumulator(statsRepository, new HaversineDistanceCalculator());
    }

    @Test
    void it_should_do_nothing_for_first_point() {
        // Given
        LocationUpdate firstPoint =
                new LocationUpdate(COURIER, T0, P0, null);

        // When
        observer().onLocationRecorded(firstPoint);

        // Then
        verifyNoInteractions(statsRepository);
    }

    @Test
    void it_should_accumulate_haversine_distance_for_subsequent_point() {
        // Given
        CourierTravelStats stats = new CourierTravelStats(COURIER, P0, T0);
        when(statsRepository.findById(COURIER)).thenReturn(Optional.of(stats));
        LocationUpdate nextPoint = new LocationUpdate(
                COURIER, T1, P1, new PreviousPoint(P0, T0));

        // When
        observer().onLocationRecorded(nextPoint);

        // Then
        assertEquals(STEP_METERS, stats.getTotalDistanceMeters(), 0.01);
    }

    @Test
    void it_should_accumulate_distance_across_multiple_points() {
        // Given
        CourierTravelStats stats = new CourierTravelStats(COURIER, P0, T0);
        when(statsRepository.findById(COURIER)).thenReturn(Optional.of(stats));

        // When
        observer().onLocationRecorded(new LocationUpdate(
                COURIER, T1, P1, new PreviousPoint(P0, T0)));
        observer().onLocationRecorded(new LocationUpdate(
                COURIER, T1.plusSeconds(20), P2, new PreviousPoint(P1, T1)));

        // Then
        assertEquals(2 * STEP_METERS, stats.getTotalDistanceMeters(), 0.02);
    }

    @Test
    void it_should_fail_fast_when_stats_missing_for_non_first_point() {
        // Given
        when(statsRepository.findById(COURIER)).thenReturn(Optional.empty());
        LocationUpdate event = new LocationUpdate(
                COURIER, T1, P0, new PreviousPoint(P1, T0));
        TravelDistanceAccumulator observer = observer();

        // When & Then
        assertThrows(IllegalStateException.class,
                () -> observer.onLocationRecorded(event));
    }
}
