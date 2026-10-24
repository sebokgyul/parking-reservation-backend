package hu.parking.reservation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Reservation(
        UUID id,
        long parkingSpotId,
        String requesterName,
        VehicleType vehicleType,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        ReservationStatus status,
        OffsetDateTime createdAt
) {
}
