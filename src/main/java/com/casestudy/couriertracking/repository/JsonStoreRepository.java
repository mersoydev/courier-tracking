package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.GeoPoint;
import com.casestudy.couriertracking.domain.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class JsonStoreRepository implements StoreRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonStoreRepository.class);
    private static final String STORES_RESOURCE = "stores.json";

    private final List<Store> stores;

    public JsonStoreRepository(ObjectMapper objectMapper) {
        this.stores = loadStores(objectMapper);
    }

    private static List<Store> loadStores(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(STORES_RESOURCE).getInputStream()) {
            List<RawStore> raw = objectMapper.readValue(input, new TypeReference<List<RawStore>>() {
            });
            List<Store> validated = validate(raw);
            log.info("Loaded {} stores: {}", validated.size(),
                    validated.stream().map(Store::name).toList());
            return validated;
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException(STORES_RESOURCE + " could not be read", exception);
        }
    }

    private static List<Store> validate(List<RawStore> raw) {
        if (raw.isEmpty()) {
            throw new IllegalStateException(
                    STORES_RESOURCE + " is empty — entrance detection is impossible without store data");
        }
        Set<String> names = new HashSet<>();
        List<Store> validated = new ArrayList<>(raw.size());
        for (RawStore data : raw) {
            String name = data.name();
            Double lat = data.lat();
            Double lng = data.lng();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(STORES_RESOURCE + ": store record without a name");
            }
            if (lat == null || lng == null) {
                throw new IllegalStateException(
                        STORES_RESOURCE + ": store with missing coordinates: " + name);
            }
            if (!names.add(name)) {
                throw new IllegalStateException(STORES_RESOURCE + ": duplicate store name: " + name);
            }
            validated.add(new Store(name, new GeoPoint(lat, lng)));
        }
        return List.copyOf(validated);
    }

    @Override
    public List<Store> findAll() {
        return stores;
    }
}
