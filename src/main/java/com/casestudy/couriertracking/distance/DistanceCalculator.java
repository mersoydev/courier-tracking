package com.casestudy.couriertracking.distance;

import com.casestudy.couriertracking.domain.GeoPoint;

public interface DistanceCalculator {

    double calculateInMeters(GeoPoint from, GeoPoint to);
}
