package com.casestudy.couriertracking.controller;

import com.casestudy.couriertracking.dto.LocationRequest;
import com.casestudy.couriertracking.service.CourierTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final CourierTrackingService trackingService;

    public LocationController(CourierTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordLocation(@Valid @RequestBody LocationRequest request) {
        trackingService.recordLocation(request.courierId(), request.time(), request.lat(), request.lng());
    }
}
