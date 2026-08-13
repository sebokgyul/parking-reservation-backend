package hu.parking.reservation.domain;

import java.time.OffsetDateTime;

public record Reservation(
        long id,
        long parkingSpotId,
        String requesterName,
        VehicleType vehicleType,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        ReservationStatus status,
        OffsetDateTime createdAt
) {
}
