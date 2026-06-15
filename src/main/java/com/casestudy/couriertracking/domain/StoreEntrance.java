package com.casestudy.couriertracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Collate;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "store_entrances",
        indexes = {
                @Index(
                        name = "idx_entrance_courier_store_time",
                        columnList = "courier_id, store_name, entrance_time"
                ),
                @Index(name = "idx_entrance_time", columnList = "entrance_time")
        }
)
public class StoreEntrance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false)
    @Collate("utf8mb4_bin")
    private String courierId;

    @Column(name = "store_name", nullable = false)
    @Collate("utf8mb4_bin")
    private String storeName;

    @Column(name = "entrance_time", nullable = false)
    private Instant entranceTime;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    protected StoreEntrance() {
    }

    public StoreEntrance(String courierId, String storeName, Instant entranceTime, GeoPoint point) {
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.storeName = Objects.requireNonNull(storeName, "storeName");
        this.entranceTime = Objects.requireNonNull(entranceTime, "entranceTime");
        Objects.requireNonNull(point, "point");
        this.lat = point.lat();
        this.lng = point.lng();
    }

    public String getCourierId() {
        return courierId;
    }

    public String getStoreName() {
        return storeName;
    }

    public Instant getEntranceTime() {
        return entranceTime;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
