package com.casestudy.couriertracking.repository;

import org.jspecify.annotations.Nullable;

record RawStore(@Nullable String name, @Nullable Double lat, @Nullable Double lng) {
}
