package com.casestudy.couriertracking.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "tracking")
public record TrackingProperties(
        @DefaultValue Entrance entrance,
        @DefaultValue("5m") Duration maxClockSkew) {

    public TrackingProperties {
        if (maxClockSkew.isNegative()) {
            throw new IllegalArgumentException("maxClockSkew must not be negative: " + maxClockSkew);
        }
    }

    public record Entrance(
            @DefaultValue("100") double radiusMeters,
            @DefaultValue("1m") Duration debounce) {

        public Entrance {
            if (radiusMeters <= 0) {
                throw new IllegalArgumentException("radiusMeters must be positive: " + radiusMeters);
            }
            if (debounce.isNegative()) {
                throw new IllegalArgumentException("debounce must not be negative: " + debounce);
            }
        }
    }
}
