package com.casestudy.couriertracking.domain;

import java.util.Objects;

public record Store(String name, GeoPoint location) {

    public Store {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
