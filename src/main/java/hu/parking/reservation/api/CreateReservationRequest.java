package hu.parking.reservation.api;

import hu.parking.reservation.domain.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateReservationRequest(
        @NotNull Long parkingSpotId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "\\P{Cntrl}*", message = "must not contain control characters")
        String requesterName,
        @NotNull VehicleType vehicleType,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {
}
