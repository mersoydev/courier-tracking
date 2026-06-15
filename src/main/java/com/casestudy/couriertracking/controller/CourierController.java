package com.casestudy.couriertracking.controller;

import com.casestudy.couriertracking.dto.TotalTravelDistanceResponse;
import com.casestudy.couriertracking.exception.CourierNotFoundException;
import com.casestudy.couriertracking.service.CourierTrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/couriers")
public class CourierController {

    private final CourierTrackingService trackingService;

    public CourierController(CourierTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{courierId}/total-travel-distance")
    public TotalTravelDistanceResponse getTotalTravelDistance(@PathVariable String courierId) {
        double totalMeters = trackingService.getTotalTravelDistance(courierId)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
        return new TotalTravelDistanceResponse(courierId, totalMeters);
    }
}
