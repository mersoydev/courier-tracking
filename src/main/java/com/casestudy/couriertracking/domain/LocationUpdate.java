package com.casestudy.couriertracking.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record LocationUpdate(
        String courierId,
        Instant time,
        GeoPoint point,
        @Nullable PreviousPoint previous) {

    public LocationUpdate {
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(point, "point");
    }

    public record PreviousPoint(GeoPoint point, Instant time) {
        public PreviousPoint {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(time, "time");
        }
    }
}
