package com.casestudy.couriertracking.repository;

import com.casestudy.couriertracking.domain.Store;

import java.util.List;

public interface StoreRepository {

    List<Store> findAll();
}
