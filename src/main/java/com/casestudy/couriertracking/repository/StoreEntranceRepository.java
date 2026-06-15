package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.StoreEntrance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreEntranceRepository extends JpaRepository<StoreEntrance, Long> {

    Optional<StoreEntrance> findTopByCourierIdAndStoreNameOrderByEntranceTimeDesc(
            String courierId, String storeName);

    List<StoreEntrance> findAllByOrderByEntranceTimeAsc();

    List<StoreEntrance> findByCourierIdOrderByEntranceTimeAsc(String courierId);
}
