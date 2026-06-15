package com.casestudy.couriertracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CourierTrackingEndToEndIT {

    private static final double ORTAKOY_LNG = 29.0210292;
    private static final double OUTSIDE_LAT = 41.057583;
    private static final double INSIDE_LAT = 41.056583;
    private static final double CENTER_LAT = 41.055783;

    @Autowired
    private MockMvc mockMvc;

    private void postLocation(String courierId, Instant time, double lat, double lng) throws Exception {
        String body = """
                { "courierId": "%s", "time": "%s", "lat": %s, "lng": %s }
                """.formatted(courierId, time, lat, lng);
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void it_should_log_one_entrance_and_accumulate_distance_over_a_route() throws Exception {
        // Given
        String courier = "e2e-route-courier";
        Instant base = Instant.now().minusSeconds(300);

        // When
        postLocation(courier, base, OUTSIDE_LAT, ORTAKOY_LNG);
        postLocation(courier, base.plusSeconds(20), INSIDE_LAT, ORTAKOY_LNG);
        postLocation(courier, base.plusSeconds(40), CENTER_LAT, ORTAKOY_LNG);

        // Then
        mockMvc.perform(get("/api/v1/couriers/{id}/total-travel-distance", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courier))
                .andExpect(jsonPath("$.totalDistanceMeters",
                        org.hamcrest.Matchers.closeTo(200.15, 0.1)));

        mockMvc.perform(get("/api/v1/store-entrances").param("courierId", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeName").value("Ortaköy MMM Migros"))
                .andExpect(jsonPath("$[0].courierId").value(courier));
    }

    @Test
    void it_should_count_a_duplicate_point_only_once() throws Exception {
        // Given
        String courier = "e2e-idempotent-courier";
        Instant base = Instant.now().minusSeconds(300);
        postLocation(courier, base, OUTSIDE_LAT, ORTAKOY_LNG);

        // When — the same second point is delivered twice (e.g. a client retry after 503)
        postLocation(courier, base.plusSeconds(20), INSIDE_LAT, ORTAKOY_LNG);
        postLocation(courier, base.plusSeconds(20), INSIDE_LAT, ORTAKOY_LNG);

        // Then — the retry is a no-op; distance is one OUTSIDE→INSIDE segment, not two
        mockMvc.perform(get("/api/v1/couriers/{id}/total-travel-distance", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceMeters",
                        org.hamcrest.Matchers.closeTo(111.19, 0.3)));
    }

    @Test
    void it_should_return_404_problem_detail_for_courier_with_no_points() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/couriers/{id}/total-travel-distance", "e2e-ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Courier Not Found"))
                .andExpect(jsonPath("$.type").value("urn:courier-tracking:problem:courier-not-found"));
    }

    @Test
    void it_should_reject_future_timestamp_without_poisoning_subsequent_points() throws Exception {
        // Given
        String courier = "e2e-future-courier";
        String futureBody = """
                { "courierId": "%s", "time": "%s", "lat": %s, "lng": %s }
                """.formatted(courier, Instant.now().plusSeconds(3600), CENTER_LAT, ORTAKOY_LNG);

        // When
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Implausible Timestamp"));

        // Then
        postLocation(courier, Instant.now().minusSeconds(60), CENTER_LAT, ORTAKOY_LNG);
        mockMvc.perform(get("/api/v1/couriers/{id}/total-travel-distance", courier))
                .andExpect(status().isOk());
    }

    @Test
    void it_should_log_entrance_when_first_point_is_already_inside_store() throws Exception {
        // Given/When
        String courier = "e2e-first-inside-courier";
        postLocation(courier, Instant.now().minusSeconds(60), CENTER_LAT, ORTAKOY_LNG);

        // Then
        mockMvc.perform(get("/api/v1/store-entrances").param("courierId", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeName").value("Ortaköy MMM Migros"));
    }

    @Test
    void it_should_expose_the_openapi_document_for_all_endpoints() throws Exception {
        // When & Then
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Courier Tracking API"))
                .andExpect(jsonPath("$.paths['/api/v1/locations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/couriers/{courierId}/total-travel-distance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/store-entrances'].get").exists());
    }
}
