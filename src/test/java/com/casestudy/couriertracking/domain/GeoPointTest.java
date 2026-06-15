package com.casestudy.couriertracking.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoPointTest {

    @Test
    void it_should_accept_valid_coordinates_including_boundaries() {
        // When & Then
        assertEquals(90.0, new GeoPoint(90.0, 0.0).lat());
        assertEquals(-90.0, new GeoPoint(-90.0, 0.0).lat());
        assertEquals(180.0, new GeoPoint(0.0, 180.0).lng());
        assertEquals(-180.0, new GeoPoint(0.0, -180.0).lng());
    }

    @Test
    void it_should_reject_out_of_range_latitude() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.0001, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-90.0001, 0.0));
    }

    @Test
    void it_should_reject_out_of_range_longitude() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, 180.0001));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, -180.0001));
    }

    @Test
    void it_should_reject_nan_coordinates() {
        // Given

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, Double.NaN));
    }
}
