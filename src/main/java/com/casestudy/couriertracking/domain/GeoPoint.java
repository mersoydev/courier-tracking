package com.casestudy.couriertracking.domain;

public record GeoPoint(double lat, double lng) {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    public GeoPoint {
        validateCoordinates(lat, lng);
    }

    private static void validateCoordinates(double lat, double lng) {
        if (isOutOfRange(lat, MIN_LATITUDE, MAX_LATITUDE)) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (isOutOfRange(lng, MIN_LONGITUDE, MAX_LONGITUDE)) {
            throw new IllegalArgumentException("Invalid longitude: " + lng);
        }
    }

    private static boolean isOutOfRange(double value, double min, double max) {
        return Double.isNaN(value) || value < min || value > max;
    }
}
