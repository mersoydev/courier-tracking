package com.casestudy.couriertracking.entrance;

import com.casestudy.couriertracking.configuration.TrackingProperties;
import com.casestudy.couriertracking.distance.HaversineDistanceCalculator;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdate.PreviousPoint;
import com.casestudy.couriertracking.domain.Store;
import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.repository.StoreEntranceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEntranceDetectorTest {

    private static final double ORTAKOY_LAT = 41.055783;
    private static final double ORTAKOY_LNG = 29.0210292;
    private static final double ATASEHIR_LAT = 40.9923307;
    private static final double ATASEHIR_LNG = 29.1244229;

    private static final Store ORTAKOY = new Store("Ortaköy MMM Migros", new GeoPoint(ORTAKOY_LAT, ORTAKOY_LNG));
    private static final Store ATASEHIR = new Store("Ataşehir MMM Migros", new GeoPoint(ATASEHIR_LAT, ATASEHIR_LNG));

    private static final String COURIER = "courier-1";
    private static final Instant BASE_TIME = Instant.parse("2026-06-13T10:00:00Z");

    private static final GeoPoint INSIDE = new GeoPoint(ORTAKOY_LAT + 0.0008, ORTAKOY_LNG);
    private static final GeoPoint INSIDE_2 = new GeoPoint(ORTAKOY_LAT + 0.0007, ORTAKOY_LNG);
    private static final GeoPoint OUTSIDE = new GeoPoint(ORTAKOY_LAT + 0.0018, ORTAKOY_LNG);
    private static final GeoPoint OUTSIDE_2 = new GeoPoint(ORTAKOY_LAT + 0.0020, ORTAKOY_LNG);

    @Mock
    private StoreEntranceRepository entranceRepository;

    @Captor
    private ArgumentCaptor<StoreEntrance> entranceCaptor;

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();
    private final TrackingProperties properties = new TrackingProperties(
            new TrackingProperties.Entrance(100, Duration.ofMinutes(1)), Duration.ofMinutes(5));

    private StoreEntranceDetector observer(Store... stores) {
        return new StoreEntranceDetector(
                () -> List.of(stores), entranceRepository, calculator, properties);
    }

    private static PreviousPoint previousAt(GeoPoint point) {
        return new PreviousPoint(point, BASE_TIME.minusSeconds(20));
    }

    private static LocationUpdate event(GeoPoint point, PreviousPoint previous) {
        return event(point, previous, BASE_TIME);
    }

    private static LocationUpdate event(GeoPoint point, PreviousPoint previous, Instant time) {
        return new LocationUpdate(COURIER, time, point, previous);
    }

    @Test
    void it_should_log_entrance_on_outside_to_inside_transition() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate event = event(INSIDE, previousAt(OUTSIDE));

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository).save(entranceCaptor.capture());
        StoreEntrance saved = entranceCaptor.getValue();
        assertEquals(COURIER, saved.getCourierId());
        assertEquals(ORTAKOY.name(), saved.getStoreName());
        assertEquals(BASE_TIME, saved.getEntranceTime());
        assertEquals(INSIDE.lat(), saved.getLat(), 1e-9);
        assertEquals(INSIDE.lng(), saved.getLng(), 1e-9);
    }

    @Test
    void it_should_not_log_new_entrance_while_staying_inside() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate event = event(INSIDE, previousAt(INSIDE_2));

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository, never()).save(any());
        verify(entranceRepository, never())
                .findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(anyString(), anyString());
    }

    @Test
    void it_should_do_nothing_while_moving_outside() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate event = event(OUTSIDE, previousAt(OUTSIDE_2));

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository, never()).save(any());
    }

    @Test
    void it_should_not_log_anything_on_exit() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate event = event(OUTSIDE, previousAt(INSIDE));

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {40, 59, 60})
    void it_should_suppress_reentry_within_debounce_window(long secondsAfterEntrance) {
        // Given
        stubLastEntrance();
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate reentry =
                event(INSIDE, previousAt(OUTSIDE), BASE_TIME.plusSeconds(secondsAfterEntrance));

        // When
        observer.onLocationRecorded(reentry);

        // Then
        verify(entranceRepository, never()).save(any());
    }

    @Test
    void it_should_log_reentry_after_debounce_window() {
        // Given
        stubLastEntrance();
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate reentry = event(INSIDE, previousAt(OUTSIDE), BASE_TIME.plusSeconds(61));

        // When
        observer.onLocationRecorded(reentry);

        // Then
        verify(entranceRepository).save(entranceCaptor.capture());
        assertEquals(BASE_TIME.plusSeconds(61), entranceCaptor.getValue().getEntranceTime());
    }

    @Test
    void it_should_count_first_point_inside_circle_as_entrance() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate firstPoint = event(INSIDE, null);

        // When
        observer.onLocationRecorded(firstPoint);

        // Then
        verify(entranceRepository).save(any(StoreEntrance.class));
    }

    @Test
    void it_should_do_nothing_for_first_point_outside_circle() {
        // Given
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate firstPoint = event(OUTSIDE, null);

        // When
        observer.onLocationRecorded(firstPoint);

        // Then
        verify(entranceRepository, never()).save(any());
    }

    @Test
    void it_should_not_create_new_entrance_after_signal_gap_while_inside() {
        // Given
        PreviousPoint lastKnownInside = new PreviousPoint(INSIDE_2, BASE_TIME.minusSeconds(1800));
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate event = event(INSIDE, lastKnownInside);

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository, never()).save(any());
    }

    @Test
    void it_should_evaluate_stores_independently() {
        // Given
        GeoPoint insideAtasehir = new GeoPoint(ATASEHIR_LAT + 0.0008, ATASEHIR_LNG);
        GeoPoint outsideAtasehir = new GeoPoint(ATASEHIR_LAT + 0.0018, ATASEHIR_LNG);
        StoreEntranceDetector observer = observer(ORTAKOY, ATASEHIR);
        LocationUpdate event = event(insideAtasehir, previousAt(outsideAtasehir));

        // When
        observer.onLocationRecorded(event);

        // Then
        verify(entranceRepository).save(entranceCaptor.capture());
        assertEquals(ATASEHIR.name(), entranceCaptor.getValue().getStoreName());
    }

    @Test
    void it_should_debounce_couriers_independently() {
        // Given
        when(entranceRepository.findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(
                "courier-2", ORTAKOY.name())).thenReturn(Optional.empty());
        StoreEntranceDetector observer = observer(ORTAKOY);
        LocationUpdate courier2Event = new LocationUpdate(
                "courier-2", BASE_TIME, INSIDE, previousAt(OUTSIDE));

        // When
        observer.onLocationRecorded(courier2Event);

        // Then
        verify(entranceRepository).save(entranceCaptor.capture());
        assertEquals("courier-2", entranceCaptor.getValue().getCourierId());
    }

    @Test
    void it_should_count_distance_equal_to_radius_as_inside() {
        // Given
        GeoPoint boundary = new GeoPoint(ORTAKOY_LAT + 0.0009, ORTAKOY_LNG);
        double exactDistance = calculator.calculateInMeters(boundary, ORTAKOY.location());
        TrackingProperties exactRadius = new TrackingProperties(
                new TrackingProperties.Entrance(exactDistance, Duration.ofMinutes(1)),
                Duration.ofMinutes(5));
        StoreEntranceDetector boundaryObserver = new StoreEntranceDetector(
                () -> List.of(ORTAKOY), entranceRepository, calculator, exactRadius);
        LocationUpdate event = event(boundary, previousAt(OUTSIDE));

        // When
        boundaryObserver.onLocationRecorded(event);

        // Then
        verify(entranceRepository).save(any(StoreEntrance.class));
    }

    private void stubLastEntrance() {
        when(entranceRepository.findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(
                COURIER, ORTAKOY.name()))
                .thenReturn(Optional.of(new StoreEntrance(
                        COURIER, ORTAKOY.name(), BASE_TIME, INSIDE)));
    }
}
