package com.casestudy.couriertracking.service;

import com.casestudy.couriertracking.configuration.TrackingProperties;
import com.casestudy.couriertracking.domain.CourierLocation;
import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdateObserver;
import com.casestudy.couriertracking.exception.ImplausibleTimestampException;
import com.casestudy.couriertracking.repository.CourierLocationRepository;
import com.casestudy.couriertracking.repository.CourierTravelStatsRepository;
import com.casestudy.couriertracking.repository.StoreEntranceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierTrackingServiceTest {

    private static final String COURIER = "courier-1";
    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(20);
    private static final Instant SENTINEL = Instant.parse("1000-01-01T00:00:00Z");

    private static final GeoPoint P0 = new GeoPoint(41.05, 29.02);

    @Mock
    private CourierLocationRepository locationRepository;

    @Mock
    private CourierTravelStatsRepository statsRepository;

    @Mock
    private StoreEntranceRepository entranceRepository;

    @Mock
    private LocationUpdateObserver firstObserver;

    @Mock
    private LocationUpdateObserver secondObserver;

    @Captor
    private ArgumentCaptor<LocationUpdate> eventCaptor;

    @Captor
    private ArgumentCaptor<CourierLocation> locationCaptor;

    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);
    private final TrackingProperties properties = new TrackingProperties(
            new TrackingProperties.Entrance(100, Duration.ofMinutes(1)), Duration.ofMinutes(5));

    private CourierTrackingService service() {
        return new CourierTrackingService(
                locationRepository, statsRepository, entranceRepository,
                List.of(firstObserver, secondObserver), fixedClock, properties);
    }

    private CourierTravelStats seededRow() {
        return new CourierTravelStats(COURIER, new GeoPoint(0, 0), SENTINEL);
    }

    @Test
    void it_should_notify_all_observers_with_previous_point_snapshot() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER))
                .thenReturn(Optional.of(new CourierTravelStats(COURIER, P0, T0)));
        when(locationRepository.existsByCourierId(COURIER)).thenReturn(true);

        // When
        service().recordLocation(COURIER, T1, 41.06, 29.03);

        // Then
        verify(firstObserver).onLocationRecorded(eventCaptor.capture());
        LocationUpdate event = eventCaptor.getValue();
        assertEquals(COURIER, event.courierId());
        assertEquals(T1, event.time());
        assertEquals(new GeoPoint(41.06, 29.03), event.point());
        LocationUpdate.PreviousPoint previous = event.previous();
        assertNotNull(previous);
        assertEquals(P0, previous.point());
        assertEquals(T0, previous.time());
        verify(secondObserver).onLocationRecorded(any(LocationUpdate.class));
    }

    @Test
    void it_should_publish_event_without_previous_for_first_point() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));

        // When
        service().recordLocation(COURIER, T0, 41.05, 29.02);

        // Then
        verify(firstObserver).onLocationRecorded(eventCaptor.capture());
        assertNull(eventCaptor.getValue().previous());
    }

    @Test
    void it_should_ensure_row_exists_for_first_point() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));

        // When
        service().recordLocation(COURIER, T0, 41.05, 29.02);

        // Then
        verify(statsRepository).ensureRow(COURIER);
        verify(statsRepository, never()).save(any());
    }

    @Test
    void it_should_update_last_point_for_subsequent_point() {
        // Given
        CourierTravelStats stats = new CourierTravelStats(COURIER, P0, T0);
        when(statsRepository.findWithLockingByCourierId(COURIER))
                .thenReturn(Optional.of(stats));
        when(locationRepository.existsByCourierId(COURIER)).thenReturn(true);

        // When
        service().recordLocation(COURIER, T1, 41.06, 29.03);

        // Then
        assertEquals(new GeoPoint(41.06, 29.03), stats.getLastPoint());
        assertEquals(T1, stats.getLastEventTime());
        verify(statsRepository, never()).save(any());
    }

    @Test
    void it_should_ignore_strictly_older_point_entirely() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER))
                .thenReturn(Optional.of(new CourierTravelStats(COURIER, P0, T0)));

        // When
        service().recordLocation(COURIER, T0.minusSeconds(1), 41.06, 29.03);

        // Then
        verify(locationRepository, never()).save(any());
        verify(firstObserver, never()).onLocationRecorded(any());
        verify(secondObserver, never()).onLocationRecorded(any());
    }

    @Test
    void it_should_process_point_with_equal_timestamp() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER))
                .thenReturn(Optional.of(new CourierTravelStats(COURIER, P0, T0)));
        when(locationRepository.existsByCourierId(COURIER)).thenReturn(true);

        // When
        service().recordLocation(COURIER, T0, 41.06, 29.03);

        // Then
        verify(locationRepository).save(any(CourierLocation.class));
        verify(firstObserver).onLocationRecorded(any(LocationUpdate.class));
    }

    @Test
    void it_should_reject_timestamp_beyond_clock_skew_tolerance() {
        // Given
        Instant tooFarInFuture = T0.plus(Duration.ofMinutes(6));
        CourierTrackingService trackingService = service();

        // When & Then
        assertThrows(ImplausibleTimestampException.class,
                () -> trackingService.recordLocation(COURIER, tooFarInFuture, 41.05, 29.02));
        verify(locationRepository, never()).save(any());
    }

    @Test
    void it_should_accept_timestamp_within_clock_skew_tolerance() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));

        // When
        service().recordLocation(COURIER, T0.plus(Duration.ofMinutes(4)), 41.05, 29.02);

        // Then
        verify(locationRepository).save(any(CourierLocation.class));
    }

    @Test
    void it_should_accept_timestamp_exactly_at_clock_skew_boundary() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));

        // When
        service().recordLocation(COURIER, T0.plus(Duration.ofMinutes(5)), 41.05, 29.02);

        // Then
        verify(locationRepository).save(any(CourierLocation.class));
    }

    @Test
    void it_should_reject_timestamp_just_past_clock_skew_boundary() {
        // Given
        Instant justPast = T0.plus(Duration.ofMinutes(5)).plusMillis(1);
        CourierTrackingService trackingService = service();

        // When & Then
        assertThrows(ImplausibleTimestampException.class,
                () -> trackingService.recordLocation(COURIER, justPast, 41.05, 29.02));
    }

    @Test
    void it_should_reject_timestamp_below_supported_range() {
        // Given
        Instant tooOld = Instant.parse("0999-12-31T23:59:59Z");
        CourierTrackingService trackingService = service();

        // When & Then
        assertThrows(ImplausibleTimestampException.class,
                () -> trackingService.recordLocation(COURIER, tooOld, 41.05, 29.02));
    }

    @Test
    void it_should_truncate_time_to_microseconds() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));
        Instant nanoTime = T0.plusNanos(123_456_789);

        // When
        service().recordLocation(COURIER, nanoTime, 41.05, 29.02);

        // Then
        verify(locationRepository).save(locationCaptor.capture());
        assertEquals(T0.plusNanos(123_456_000), locationCaptor.getValue().getEventTime());
    }

    @Test
    void it_should_persist_accepted_point_as_raw_audit_record() {
        // Given
        when(statsRepository.findWithLockingByCourierId(COURIER)).thenReturn(Optional.of(seededRow()));

        // When
        service().recordLocation(COURIER, T0, 41.05, 29.02);

        // Then
        verify(locationRepository).save(locationCaptor.capture());
        CourierLocation saved = locationCaptor.getValue();
        assertEquals(COURIER, saved.getCourierId());
        assertEquals(T0, saved.getEventTime());
        assertEquals(41.05, saved.getLat(), 1e-9);
        assertEquals(29.02, saved.getLng(), 1e-9);
    }

    @Test
    void it_should_read_accumulated_row_for_total_travel_distance() {
        // Given
        CourierTravelStats stats = new CourierTravelStats(COURIER, P0, T0);
        stats.addDistance(1523.7);
        when(statsRepository.findById(COURIER)).thenReturn(Optional.of(stats));

        // When
        Optional<Double> total = service().getTotalTravelDistance(COURIER);

        // Then
        assertEquals(Optional.of(1523.7), total);
    }

    @Test
    void it_should_return_empty_total_distance_for_unknown_courier() {
        // Given
        when(statsRepository.findById("ghost")).thenReturn(Optional.empty());

        // When
        Optional<Double> total = service().getTotalTravelDistance("ghost");

        // Then
        assertTrue(total.isEmpty());
    }

    @Test
    void it_should_return_all_store_entrances_time_ordered() {
        // Given
        List<StoreEntrance> entrances = List.of(
                new StoreEntrance(COURIER, "Ortaköy MMM Migros", T0, new GeoPoint(41.0558, 29.0210)));
        when(entranceRepository.findAllByOrderByEntranceTimeAsc()).thenReturn(entrances);

        // When
        List<StoreEntrance> result = service().getAllStoreEntrances();

        // Then
        assertEquals(entrances, result);
    }

    @Test
    void it_should_return_courier_store_entrances_time_ordered() {
        // Given
        List<StoreEntrance> entrances = List.of(
                new StoreEntrance(COURIER, "Ortaköy MMM Migros", T0, new GeoPoint(41.0558, 29.0210)));
        when(entranceRepository.findByCourierIdOrderByEntranceTimeAsc(COURIER)).thenReturn(entrances);

        // When
        List<StoreEntrance> result = service().getStoreEntrances(COURIER);

        // Then
        assertEquals(entrances, result);
    }
}
