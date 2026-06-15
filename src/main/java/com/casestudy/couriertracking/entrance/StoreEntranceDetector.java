package com.casestudy.couriertracking.entrance;

import com.casestudy.couriertracking.configuration.TrackingProperties;
import com.casestudy.couriertracking.distance.DistanceCalculator;
import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdateObserver;
import com.casestudy.couriertracking.domain.Store;
import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.repository.StoreEntranceRepository;
import com.casestudy.couriertracking.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class StoreEntranceDetector implements LocationUpdateObserver {

    private static final Logger log = LoggerFactory.getLogger(StoreEntranceDetector.class);

    private final StoreRepository storeRepository;
    private final StoreEntranceRepository entranceRepository;
    private final DistanceCalculator distanceCalculator;
    private final TrackingProperties properties;

    public StoreEntranceDetector(StoreRepository storeRepository,
                                 StoreEntranceRepository entranceRepository,
                                 DistanceCalculator distanceCalculator,
                                 TrackingProperties properties) {
        this.storeRepository = storeRepository;
        this.entranceRepository = entranceRepository;
        this.distanceCalculator = distanceCalculator;
        this.properties = properties;
    }

    @Override
    public void onLocationRecorded(LocationUpdate event) {
        for (Store store : storeRepository.findAll()) {
            if (isEntranceTransition(event, store)) {
                handleTransition(event, store);
            }
        }
    }

    private boolean isEntranceTransition(LocationUpdate event, Store store) {
        return isInside(event.point(), store) && wasOutside(event, store);
    }

    private boolean wasOutside(LocationUpdate event, Store store) {
        LocationUpdate.PreviousPoint previous = event.previous();
        return previous == null || !isInside(previous.point(), store);
    }

    private void handleTransition(LocationUpdate event, Store store) {
        Optional<StoreEntrance> lastEntrance = entranceRepository
                .findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(event.courierId(), store.name());

        if (lastEntrance.isPresent() && isWithinDebounce(lastEntrance.get(), event)) {
            log.debug("Entrance suppressed (debounce): courier={} store={} time={}",
                    event.courierId(), store.name(), event.time());
            return;
        }

        entranceRepository.save(new StoreEntrance(
                event.courierId(), store.name(), event.time(), event.point()));
        log.info("Store entrance: courier={} store={} time={}",
                event.courierId(), store.name(), event.time());
    }

    private boolean isInside(GeoPoint point, Store store) {
        double distance = distanceCalculator.calculateInMeters(point, store.location());
        return distance <= properties.entrance().radiusMeters();
    }

    private boolean isWithinDebounce(StoreEntrance lastEntrance, LocationUpdate event) {
        Duration elapsed = Duration.between(lastEntrance.getEntranceTime(), event.time());
        return elapsed.compareTo(properties.entrance().debounce()) <= 0;
    }
}
