package hu.parking.reservation.service;

import hu.parking.reservation.api.ApiException;
import hu.parking.reservation.api.CreateReservationRequest;
import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.SpotType;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.repository.ParkingSpotRepository;
import hu.parking.reservation.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReservationService {
    private final ParkingSpotRepository parkingSpotRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Autowired
    public ReservationService(ParkingSpotRepository parkingSpotRepository, ReservationRepository reservationRepository) {
        this(parkingSpotRepository, reservationRepository, Clock.systemUTC());
    }

    ReservationService(ParkingSpotRepository parkingSpotRepository, ReservationRepository reservationRepository, Clock clock) {
        this.parkingSpotRepository = parkingSpotRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    public List<ParkingSpot> listParkingSpots() {
        return parkingSpotRepository.findAll();
    }

    public List<Reservation> findReservations(long spotId, OffsetDateTime from, OffsetDateTime to) {
        validateTimeRange(from, to, "from must be before to");
        requireSpot(spotId);
        return reservationRepository.findActiveOverlapping(spotId, from, to);
    }

    @Transactional
    public Reservation create(CreateReservationRequest request) {
        validateRequest(request);
        ParkingSpot spot = requireSpot(request.parkingSpotId());
        if (!spot.active()) {
            throw new ApiException(HttpStatus.CONFLICT, "Parking spot is inactive");
        }
        if (spot.spotType() == SpotType.ELECTRIC && request.vehicleType() != VehicleType.ELECTRIC) {
            throw new ApiException(HttpStatus.CONFLICT, "Electric parking spots require an electric vehicle");
        }
        if (reservationRepository.hasActiveOverlap(request.parkingSpotId(), request.startTime(), request.endTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "Parking spot is already reserved for the requested time interval");
        }
        try {
            return reservationRepository.create(request.parkingSpotId(), request.requesterName().trim(), request.vehicleType(), request.startTime(), request.endTime());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Parking spot is already reserved for the requested time interval");
        }
    }

    @Transactional
    public Reservation cancel(long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Reservation is already cancelled");
        }
        return reservationRepository.cancel(reservationId);
    }

    private ParkingSpot requireSpot(long spotId) {
        return parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parking spot not found"));
    }

    private void validateRequest(CreateReservationRequest request) {
        if (request == null || request.parkingSpotId() == null || request.requesterName() == null || request.vehicleType() == null
                || request.startTime() == null || request.endTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "parkingSpotId, requesterName, vehicleType, startTime and endTime are required");
        }
        if (request.requesterName().trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requesterName must not be blank");
        }
        if (request.requesterName().trim().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requesterName must not exceed 100 characters");
        }
        validateTimeRange(request.startTime(), request.endTime(), "startTime must be before endTime");
        if (request.startTime().toInstant().isBefore(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startTime must not be in the past");
        }
    }

    private void validateTimeRange(OffsetDateTime start, OffsetDateTime end, String message) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        if (start.getSecond() != 0 || start.getNano() != 0 || end.getSecond() != 0 || end.getNano() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Time values must have minute precision");
        }
    }
}
