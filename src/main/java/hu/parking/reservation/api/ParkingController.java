package hu.parking.reservation.api;

import hu.parking.reservation.domain.OccupiedInterval;
import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.service.NewReservation;
import hu.parking.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class ParkingController {
    private final ReservationService reservationService;

    public ParkingController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/parking-spots")
    public List<ParkingSpot> listParkingSpots() {
        return reservationService.listParkingSpots();
    }

    @GetMapping("/parking-spots/{spotId}/reservations")
    public List<OccupiedInterval> listReservations(
            @PathVariable long spotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return reservationService.findReservations(spotId, from, to);
    }

    @PostMapping("/reservations")
    public ResponseEntity<Reservation> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        NewReservation newReservation = new NewReservation(request.parkingSpotId(), request.requesterName(),
                request.vehicleType(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(newReservation));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public Reservation cancelReservation(@PathVariable UUID reservationId) {
        return reservationService.cancel(reservationId);
    }
}
