package com.casestudy.couriertracking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casestudy.couriertracking.service.CourierTrackingService;

@WebMvcTest(LocationController.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierTrackingService trackingService;

    private static final String VALID_BODY = """
            {
              "courierId": "courier-1",
              "time": "2026-06-13T10:00:00Z",
              "lat": 41.055783,
              "lng": 29.0210292
            }
            """;

    @Test
    void it_should_accept_valid_location_with_202() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted());

        verify(trackingService).recordLocation(
                "courier-1", Instant.parse("2026-06-13T10:00:00Z"), 41.055783, 29.0210292);
    }

    @Test
    void it_should_reject_longitude_out_of_range_with_400() throws Exception {
        // Given
        String body = VALID_BODY.replace("29.0210292", "181.0");

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lng").exists());

        verify(trackingService, never()).recordLocation(anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    void it_should_reject_courier_id_over_255_chars_with_400() throws Exception {
        // Given
        String tooLong = "c".repeat(256);
        String body = VALID_BODY.replace("courier-1", tooLong);

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courierId").exists());

        verify(trackingService, never()).recordLocation(anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    void it_should_reject_latitude_out_of_range_with_400() throws Exception {
        // Given
        String body = VALID_BODY.replace("41.055783", "91.0");

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").exists());

        verify(trackingService, never()).recordLocation(anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    void it_should_reject_blank_courier_id_with_400() throws Exception {
        // Given
        String body = VALID_BODY.replace("courier-1", "   ");

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courierId").exists());
    }

    @Test
    void it_should_reject_missing_time_with_400() throws Exception {
        // Given
        String body = """
                { "courierId": "courier-1", "lat": 41.0, "lng": 29.0 }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.time").exists());
    }

    @Test
    void it_should_reject_missing_latitude_with_400() throws Exception {
        // Given
        String body = """
                { "courierId": "courier-1", "time": "2026-06-13T10:00:00Z", "lng": 29.0 }
                """;

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").exists());
    }

    @Test
    void it_should_reject_zoneless_timestamp_with_400() throws Exception {
        // Given
        String body = VALID_BODY.replace("2026-06-13T10:00:00Z", "2026-06-13T10:00:00");

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(trackingService, never()).recordLocation(anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    void it_should_accept_offset_timestamp_and_normalize_to_utc() throws Exception {
        // Given
        String body = VALID_BODY.replace("2026-06-13T10:00:00Z", "2026-06-13T13:00:00+03:00");

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(trackingService).recordLocation(
                "courier-1", Instant.parse("2026-06-13T10:00:00Z"), 41.055783, 29.0210292);
    }

    @Test
    void it_should_reject_malformed_json_with_400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ bozuk json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void it_should_map_implausible_timestamp_to_400_problem_detail() throws Exception {
        // Given
        org.mockito.Mockito.doThrow(
                        new com.casestudy.couriertracking.exception.ImplausibleTimestampException(
                                "time gelecekte"))
                .when(trackingService).recordLocation(anyString(), any(), anyDouble(), anyDouble());

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Implausible Timestamp"));
    }

    @Test
    void it_should_map_unexpected_exception_to_500_problem_detail_without_details() throws Exception {
        // Given
        org.mockito.Mockito.doThrow(new IllegalStateException("iç detay: gizli kalmalı"))
                .when(trackingService).recordLocation(anyString(), any(), anyDouble(), anyDouble());

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void it_should_map_lock_contention_to_503_with_retry_after() throws Exception {
        // Given
        org.mockito.Mockito.doThrow(
                        new org.springframework.dao.PessimisticLockingFailureException("lock wait timeout"))
                .when(trackingService).recordLocation(anyString(), any(), anyDouble(), anyDouble());

        // When & Then
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.title").value("Temporary Contention"))
                .andExpect(jsonPath("$.type").value("urn:courier-tracking:problem:temporary-contention"));
    }
}
