package com.casestudy.couriertracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Collate;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "courier_locations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_location_point",
                columnNames = {"courier_id", "event_time", "lat", "lng"})
)
public class CourierLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false)
    @Collate("utf8mb4_bin")
    private String courierId;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    protected CourierLocation() {
    }

    public CourierLocation(String courierId, Instant eventTime, GeoPoint point) {
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.eventTime = Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(point, "point");
        this.lat = point.lat();
        this.lng = point.lng();
    }

    public String getCourierId() {
        return courierId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
