package com.casestudy.couriertracking.domain;

public interface LocationUpdateObserver {

    void onLocationRecorded(LocationUpdate event);
}
