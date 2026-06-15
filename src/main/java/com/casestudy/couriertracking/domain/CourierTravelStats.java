package com.casestudy.couriertracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Collate;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "courier_travel_stats")
public class CourierTravelStats {

    @Id
    @Column(name = "courier_id")
    @Collate("utf8mb4_bin")
    private String courierId;

    @Column(name = "total_distance_meters", nullable = false)
    private double totalDistanceMeters;

    @Column(name = "last_lat", nullable = false)
    private double lastLat;

    @Column(name = "last_lng", nullable = false)
    private double lastLng;

    @Column(name = "last_event_time", nullable = false)
    private Instant lastEventTime;

    protected CourierTravelStats() {
    }

    public CourierTravelStats(String courierId, GeoPoint point, Instant time) {
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(point, "point");
        this.totalDistanceMeters = 0.0;
        this.lastLat = point.lat();
        this.lastLng = point.lng();
        this.lastEventTime = Objects.requireNonNull(time, "time");
    }

    public void addDistance(double meters) {
        if (!Double.isFinite(meters) || meters < 0.0) {
            throw new IllegalArgumentException("Invalid distance: " + meters);
        }
        this.totalDistanceMeters += meters;
    }

    public void recordPoint(GeoPoint point, Instant time) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(time, "time");
        if (time.isBefore(this.lastEventTime)) {
            throw new IllegalArgumentException(
                    "lastEventTime must not move backwards: " + time + " < " + this.lastEventTime);
        }
        this.lastLat = point.lat();
        this.lastLng = point.lng();
        this.lastEventTime = time;
    }

    public String getCourierId() {
        return courierId;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public GeoPoint getLastPoint() {
        return new GeoPoint(lastLat, lastLng);
    }

    public Instant getLastEventTime() {
        return lastEventTime;
    }
}
