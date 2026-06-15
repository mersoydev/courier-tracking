package com.casestudy.couriertracking.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingPropertiesTest {

    @Test
    void it_should_accept_valid_values() {
        // When
        TrackingProperties props = new TrackingProperties(
                new TrackingProperties.Entrance(100, Duration.ofMinutes(1)), Duration.ofMinutes(5));

        // Then
        assertEquals(100.0, props.entrance().radiusMeters(), 1e-9);
        assertEquals(Duration.ofMinutes(1), props.entrance().debounce());
        assertEquals(Duration.ofMinutes(5), props.maxClockSkew());
    }

    @Test
    void it_should_reject_non_positive_radius() {
        // Given
        Duration oneMinute = Duration.ofMinutes(1);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingProperties.Entrance(0, oneMinute));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingProperties.Entrance(-5, oneMinute));
    }

    @Test
    void it_should_reject_negative_debounce() {
        // Given
        Duration negative = Duration.ofMinutes(-1);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingProperties.Entrance(100, negative));
    }

    @Test
    void it_should_reject_negative_clock_skew() {
        // Given
        TrackingProperties.Entrance entrance =
                new TrackingProperties.Entrance(100, Duration.ofMinutes(1));
        Duration negative = Duration.ofMinutes(-1);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingProperties(entrance, negative));
    }
}
