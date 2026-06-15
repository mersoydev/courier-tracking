package com.casestudy.couriertracking.exception;

public class CourierNotFoundException extends RuntimeException {

    private final String courierId;

    public CourierNotFoundException(String courierId) {
        super("Courier not found: " + courierId);
        this.courierId = courierId;
    }

    public String getCourierId() {
        return courierId;
    }
}
