CREATE TABLE courier_travel_stats (
    courier_id            VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    total_distance_meters DOUBLE       NOT NULL,
    last_lat              DOUBLE       NOT NULL,
    last_lng              DOUBLE       NOT NULL,
    last_event_time       DATETIME(6)  NOT NULL,
    PRIMARY KEY (courier_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE courier_locations (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    courier_id  VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    event_time  DATETIME(6)  NOT NULL,
    lat         DOUBLE       NOT NULL,
    lng         DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_location_point (courier_id, event_time, lat, lng)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE store_entrances (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    courier_id    VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    store_name    VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    entrance_time DATETIME(6)  NOT NULL,
    lat           DOUBLE       NOT NULL,
    lng           DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_entrance_courier_store_time (courier_id, store_name, entrance_time),
    KEY idx_entrance_time (entrance_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
