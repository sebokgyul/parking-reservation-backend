package hu.parking.reservation.api;

import hu.parking.reservation.domain.VehicleType;

import java.time.OffsetDateTime;

public record CreateReservationRequest(
        Long parkingSpotId,
        String requesterName,
        VehicleType vehicleType,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}
