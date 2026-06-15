package com.casestudy.couriertracking.distance;

import com.casestudy.couriertracking.domain.GeoPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HaversineDistanceCalculatorTest {

    private static final double METERS_PER_DEGREE_ARC = 111_194.9266;

    private static final GeoPoint ORTAKOY = new GeoPoint(41.055783, 29.0210292);
    private static final GeoPoint ATASEHIR = new GeoPoint(40.9923307, 29.1244229);
    private static final GeoPoint NOVADA = new GeoPoint(40.986106, 29.1161293);

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void it_should_return_zero_distance_for_same_point() {
        // When
        double distance = calculator.calculateInMeters(ORTAKOY, ORTAKOY);

        // Then
        assertEquals(0.0, distance, 1e-9);
    }

    @Test
    void it_should_be_symmetric() {
        // When
        double aToB = calculator.calculateInMeters(ATASEHIR, NOVADA);
        double bToA = calculator.calculateInMeters(NOVADA, ATASEHIR);

        // Then
        assertEquals(aToB, bToA, 1e-9);
    }

    @Test
    void it_should_match_analytic_value_for_latitude_arc() {
        // Given
        double northOffsetDegrees = 0.0009;
        GeoPoint north = new GeoPoint(ORTAKOY.lat() + northOffsetDegrees, ORTAKOY.lng());

        // When
        double distance = calculator.calculateInMeters(ORTAKOY, north);

        // Then
        assertEquals(METERS_PER_DEGREE_ARC * northOffsetDegrees, distance, 0.01);
    }

    @Test
    void it_should_match_analytic_value_for_longitude_arc_at_equator() {
        // When
        double distance = calculator.calculateInMeters(
                new GeoPoint(0.0, 0.0), new GeoPoint(0.0, 1.0));

        // Then
        assertEquals(METERS_PER_DEGREE_ARC, distance, 0.01);
    }

    @Test
    void it_should_pass_real_store_distance_sanity_check() {
        // When
        double distance = calculator.calculateInMeters(ATASEHIR, NOVADA);

        // Then
        assertEquals(981.7, distance, 3.0);
    }

    @Test
    void it_should_stay_finite_for_near_antipodal_points() {
        // Given
        GeoPoint nearNorth = new GeoPoint(0.00709, 0.0);
        GeoPoint nearAntipode = new GeoPoint(-0.00709, 180.0);

        // When
        double distance = calculator.calculateInMeters(nearNorth, nearAntipode);

        // Then
        assertFalse(Double.isNaN(distance), "anti-podal çiftte NaN üretilmemeli");
        assertEquals(20_015_086.8, distance, 5.0);
    }
}
