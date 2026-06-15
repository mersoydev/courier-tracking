package com.casestudy.couriertracking.controller;

import com.casestudy.couriertracking.service.CourierTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierController.class)
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierTrackingService trackingService;

    @Test
    void it_should_return_total_distance_for_known_courier() throws Exception {
        // Given
        when(trackingService.getTotalTravelDistance("courier-1"))
                .thenReturn(Optional.of(1523.7));

        // When & Then
        mockMvc.perform(get("/api/v1/couriers/courier-1/total-travel-distance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value("courier-1"))
                .andExpect(jsonPath("$.totalDistanceMeters").value(1523.7));
    }

    @Test
    void it_should_return_404_problem_detail_for_unknown_courier() throws Exception {
        // Given
        when(trackingService.getTotalTravelDistance("ghost")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/couriers/ghost/total-travel-distance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Courier Not Found"))
                .andExpect(jsonPath("$.detail").value("Courier not found: ghost"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("urn:courier-tracking:problem:courier-not-found"))
                .andExpect(jsonPath("$.courierId").value("ghost"));
    }
}
