package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.Store;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonStoreRepositoryTest {

    @Test
    void it_should_load_and_validate_all_stores_from_classpath() {
        // When
        JsonStoreRepository repository = new JsonStoreRepository(JsonMapper.builder().build());

        // Then
        List<Store> stores = repository.findAll();
        assertEquals(5, stores.size());
        Store ortakoy = stores.stream()
                .filter(store -> store.name().equals("Ortaköy MMM Migros"))
                .findFirst().orElseThrow();
        assertEquals(new GeoPoint(41.055783, 29.0210292), ortakoy.location());
    }

    @Test
    void it_should_fail_fast_on_empty_store_list() {
        // Given
        ObjectMapper mapper = mockReturning(List.of());

        // When & Then
        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(mapper));
    }

    @Test
    void it_should_fail_fast_on_store_with_blank_name() {
        // Given
        ObjectMapper mapper = mockReturning(List.of(new RawStore("  ", 41.0, 29.0)));

        // When & Then
        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(mapper));
    }

    @Test
    void it_should_fail_fast_on_store_with_missing_coordinates() {
        // Given
        ObjectMapper mapper = mockReturning(List.of(new RawStore("Migros", null, 29.0)));

        // When & Then
        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(mapper));
    }

    @Test
    void it_should_fail_fast_on_store_with_out_of_range_coordinates() {
        // Given
        ObjectMapper mapper = mockReturning(List.of(new RawStore("Migros", 91.0, 29.0)));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new JsonStoreRepository(mapper));
    }

    @Test
    void it_should_fail_fast_on_duplicate_store_names() {
        // Given
        ObjectMapper mapper = mockReturning(List.of(
                new RawStore("Migros", 41.0, 29.0),
                new RawStore("Migros", 41.1, 29.1)));

        // When & Then
        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(mapper));
    }

    private static ObjectMapper mockReturning(List<RawStore> stores) {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(any(InputStream.class), ArgumentMatchers.<TypeReference<List<RawStore>>>any()))
                .thenReturn(stores);
        return mapper;
    }
}
