package hu.parking.reservation.domain;

import java.time.OffsetDateTime;

public record OccupiedInterval(OffsetDateTime startTime, OffsetDateTime endTime) {
}
