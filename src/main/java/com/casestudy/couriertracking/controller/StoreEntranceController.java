package com.casestudy.couriertracking.controller;

import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.dto.StoreEntranceResponse;
import com.casestudy.couriertracking.service.CourierTrackingService;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/store-entrances")
public class StoreEntranceController {

    private final CourierTrackingService trackingService;

    public StoreEntranceController(CourierTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping
    public List<StoreEntranceResponse> getStoreEntrances(@RequestParam(required = false)
                                                         @Nullable String courierId) {
        List<StoreEntrance> entrances = (courierId == null) ?
                trackingService.getAllStoreEntrances() :
                trackingService.getStoreEntrances(courierId);
        return entrances.stream().map(StoreEntranceResponse::from).toList();
    }
}
