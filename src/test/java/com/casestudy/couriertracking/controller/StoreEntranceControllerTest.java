package com.casestudy.couriertracking.controller;

import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.StoreEntrance;
import com.casestudy.couriertracking.service.CourierTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreEntranceController.class)
class StoreEntranceControllerTest {

    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierTrackingService trackingService;

    @Test
    void it_should_return_entrances_filtered_by_courier() throws Exception {
        // Given
        when(trackingService.getStoreEntrances("courier-1")).thenReturn(List.of(
                new StoreEntrance("courier-1", "Ortaköy MMM Migros", T0, new GeoPoint(41.0558, 29.0210))));

        // When & Then
        mockMvc.perform(get("/api/v1/store-entrances").param("courierId", "courier-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courierId").value("courier-1"))
                .andExpect(jsonPath("$[0].storeName").value("Ortaköy MMM Migros"))
                .andExpect(jsonPath("$[0].entranceTime").value("2026-06-13T10:00:00Z"))
                .andExpect(jsonPath("$[0].lat").value(41.0558))
                .andExpect(jsonPath("$[0].lng").value(29.0210));

        verify(trackingService, never()).getAllStoreEntrances();
    }

    @Test
    void it_should_return_all_entrances_when_no_filter_given() throws Exception {
        // Given
        when(trackingService.getAllStoreEntrances()).thenReturn(List.of(
                new StoreEntrance("courier-1", "Ortaköy MMM Migros", T0, new GeoPoint(41.0558, 29.0210)),
                new StoreEntrance("courier-2", "Ataşehir MMM Migros", T0.plusSeconds(60),
                        new GeoPoint(40.9923, 29.1244))));

        // When & Then
        mockMvc.perform(get("/api/v1/store-entrances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].courierId").value("courier-2"));
    }

    @Test
    void it_should_return_empty_list_when_no_entrances_exist() throws Exception {
        // Given
        when(trackingService.getAllStoreEntrances()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/store-entrances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
