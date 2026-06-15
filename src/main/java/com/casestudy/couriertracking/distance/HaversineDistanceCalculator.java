package com.casestudy.couriertracking.distance;

import com.casestudy.couriertracking.domain.GeoPoint;
import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    static final double EARTH_RADIUS_METERS = 6_371_000.0;

    @Override
    public double calculateInMeters(GeoPoint from, GeoPoint to) {
        double dLat = Math.toRadians(to.lat() - from.lat());
        double dLng = Math.toRadians(to.lng() - from.lng());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.lat())) * Math.cos(Math.toRadians(to.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        a = Math.min(1.0, a);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
