package com.casestudy.couriertracking.distance;

import com.casestudy.couriertracking.domain.CourierTravelStats;
import com.casestudy.couriertracking.domain.LocationUpdate;
import com.casestudy.couriertracking.domain.LocationUpdateObserver;
import com.casestudy.couriertracking.repository.CourierTravelStatsRepository;
import org.springframework.stereotype.Component;

@Component
public class TravelDistanceAccumulator implements LocationUpdateObserver {

    private final CourierTravelStatsRepository statsRepository;
    private final DistanceCalculator distanceCalculator;

    public TravelDistanceAccumulator(CourierTravelStatsRepository statsRepository,
                                     DistanceCalculator distanceCalculator) {
        this.statsRepository = statsRepository;
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public void onLocationRecorded(LocationUpdate event) {
        LocationUpdate.PreviousPoint previous = event.previous();
        if (previous == null) {
            return;
        }

        CourierTravelStats stats = statsRepository.findById(event.courierId())
                .orElseThrow(() -> new IllegalStateException(
                        "Stats row missing for a courier that has a previous point: " + event.courierId()));

        stats.addDistance(distanceCalculator.calculateInMeters(
                previous.point(), event.point()));
    }
}
