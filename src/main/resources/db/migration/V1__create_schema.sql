CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE parking_spots (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    spot_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT parking_spots_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT parking_spots_type_valid CHECK (spot_type IN ('NORMAL', 'ELECTRIC'))
);

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    parking_spot_id BIGINT NOT NULL REFERENCES parking_spots(id),
    requester_name VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservations_requester_name_not_blank CHECK (btrim(requester_name) <> ''),
    CONSTRAINT reservations_vehicle_type_valid CHECK (vehicle_type IN ('STANDARD', 'ELECTRIC')),
    CONSTRAINT reservations_status_valid CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT reservations_time_range_valid CHECK (start_time < end_time),
    CONSTRAINT reservations_start_time_minute_precision CHECK (EXTRACT(SECOND FROM start_time) = 0),
    CONSTRAINT reservations_end_time_minute_precision CHECK (EXTRACT(SECOND FROM end_time) = 0),
    CONSTRAINT reservations_no_active_time_overlap
        EXCLUDE USING gist (
            parking_spot_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        ) WHERE (status = 'ACTIVE')
);

CREATE INDEX reservations_active_spot_time_idx
    ON reservations (parking_spot_id, start_time, end_time)
    WHERE status = 'ACTIVE';
