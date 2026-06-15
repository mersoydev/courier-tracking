package com.casestudy.couriertracking.service;

import com.casestudy.couriertracking.configuration.TrackingProperties;
import com.casestudy.couriertracking.domain.CourierLocation;
import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdate.PreviousPoint;
import com.casestudy.couriertracking.domain.LocationUpdateObserver;
import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.exception.ImplausibleTimestampException;
import com.casestudy.couriertracking.repository.CourierLocationRepository;
import com.casestudy.couriertracking.repository.CourierTravelStatsRepository;
import com.casestudy.couriertracking.repository.StoreEntranceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class CourierTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CourierTrackingService.class);

    static final Instant MIN_SUPPORTED_TIME = Instant.parse("1000-01-01T00:00:00Z");

    private final CourierLocationRepository locationRepository;
    private final CourierTravelStatsRepository statsRepository;
    private final StoreEntranceRepository entranceRepository;
    private final List<LocationUpdateObserver> observers;
    private final Clock clock;
    private final TrackingProperties properties;

    public CourierTrackingService(CourierLocationRepository locationRepository,
                                  CourierTravelStatsRepository statsRepository,
                                  StoreEntranceRepository entranceRepository,
                                  List<LocationUpdateObserver> observers,
                                  Clock clock,
                                  TrackingProperties properties) {
        this.locationRepository = locationRepository;
        this.statsRepository = statsRepository;
        this.entranceRepository = entranceRepository;
        this.observers = observers;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public void recordLocation(String courierId, Instant time, double lat, double lng) {
        Instant eventTime = time.truncatedTo(ChronoUnit.MICROS);
        rejectImplausibleTime(eventTime);
        GeoPoint point = new GeoPoint(lat, lng);

        statsRepository.ensureRow(courierId);
        CourierTravelStats stats = statsRepository.findWithLockingByCourierId(courierId).orElseThrow();

        if (eventTime.isBefore(stats.getLastEventTime())) {
            log.warn("Out-of-order location ignored: courier={} time={} lastKnown={}",
                    courierId, eventTime, stats.getLastEventTime());
            return;
        }

        if (locationRepository.existsByCourierIdAndEventTimeAndLatAndLng(courierId, eventTime, lat, lng)) {
            log.debug("Duplicate location ignored (idempotent retry): courier={} time={}", courierId, eventTime);
            return;
        }

        boolean firstPoint = !locationRepository.existsByCourierId(courierId);

        locationRepository.save(new CourierLocation(courierId, eventTime, point));

        PreviousPoint previous = firstPoint ? null : new PreviousPoint(stats.getLastPoint(), stats.getLastEventTime());
        LocationUpdate event = new LocationUpdate(courierId, eventTime, point, previous);

        for (LocationUpdateObserver observer : observers) {
            observer.onLocationRecorded(event);
        }

        stats.recordPoint(point, eventTime);
    }

    private void rejectImplausibleTime(Instant time) {
        Instant upperBound = clock.instant().plus(properties.maxClockSkew());
        if (time.isAfter(upperBound)) {
            throw new ImplausibleTimestampException(
                    "time is in the future: " + time + " (upper acceptance bound: " + upperBound + ")");
        }
        if (time.isBefore(MIN_SUPPORTED_TIME)) {
            throw new ImplausibleTimestampException(
                    "time is below the supported range: " + time + " (lower bound: " + MIN_SUPPORTED_TIME + ")");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Double> getTotalTravelDistance(String courierId) {
        return statsRepository.findById(courierId)
                .map(CourierTravelStats::getTotalDistanceMeters);
    }

    @Transactional(readOnly = true)
    public List<StoreEntrance> getAllStoreEntrances() {
        return entranceRepository.findAllByOrderByEntranceTimeAsc();
    }

    @Transactional(readOnly = true)
    public List<StoreEntrance> getStoreEntrances(String courierId) {
        return entranceRepository.findByCourierIdOrderByEntranceTimeAsc(courierId);
    }
}
