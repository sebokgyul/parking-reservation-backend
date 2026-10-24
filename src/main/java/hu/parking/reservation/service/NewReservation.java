package hu.parking.reservation.service;

import hu.parking.reservation.domain.VehicleType;

import java.time.OffsetDateTime;

public record NewReservation(
        long parkingSpotId,
        String requesterName,
        VehicleType vehicleType,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}
