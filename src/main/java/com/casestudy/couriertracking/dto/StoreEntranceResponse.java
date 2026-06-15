package com.casestudy.couriertracking.dto;

import com.casestudy.couriertracking.domain.StoreEntrance;

import java.time.Instant;

public record StoreEntranceResponse(
        String courierId,
        String storeName,
        Instant entranceTime,
        double lat,
        double lng) {

    public static StoreEntranceResponse from(StoreEntrance entrance) {
        return new StoreEntranceResponse(
                entrance.getCourierId(),
                entrance.getStoreName(),
                entrance.getEntranceTime(),
                entrance.getLat(),
                entrance.getLng());
    }
}
