package hu.parking.reservation.service;

import hu.parking.reservation.domain.OccupiedInterval;
import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.SpotType;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.repository.ParkingSpotRepository;
import hu.parking.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private static final String OVERLAP_MESSAGE = "Parking spot is already reserved for the requested time interval";
    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Duration MAX_ADVANCE = Duration.ofDays(90);

    private final ParkingSpotRepository parkingSpotRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationService(ParkingSpotRepository parkingSpotRepository, ReservationRepository reservationRepository, Clock clock) {
        this.parkingSpotRepository = parkingSpotRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ParkingSpot> listParkingSpots() {
        return parkingSpotRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<OccupiedInterval> findReservations(long spotId, OffsetDateTime from, OffsetDateTime to) {
        validateRange(from, to, "from must be before to");
        requireSpot(spotId);
        return reservationRepository.findActiveIntervals(spotId, from, to);
    }

    @Transactional(readOnly = true)
    public Reservation findReservation(UUID reservationId) {
        return requireReservation(reservationId);
    }

    @Transactional
    public Reservation create(NewReservation request) {
        validateRequest(request);
        ParkingSpot spot = requireSpot(request.parkingSpotId());
        if (!spot.active()) {
            throw new ConflictException("Parking spot is inactive");
        }
        if (spot.spotType() == SpotType.ELECTRIC && request.vehicleType() != VehicleType.ELECTRIC) {
            throw new ConflictException("Electric parking spots require an electric vehicle");
        }
        if (reservationRepository.hasActiveOverlap(request.parkingSpotId(), request.startTime(), request.endTime())) {
            throw new ConflictException(OVERLAP_MESSAGE);
        }
        return reservationRepository.create(request.parkingSpotId(), request.requesterName().trim(), request.vehicleType(), request.startTime(), request.endTime())
                .orElseThrow(() -> new ConflictException(OVERLAP_MESSAGE));
    }

    @Transactional
    public Reservation cancel(UUID reservationId) {
        Reservation reservation = requireReservation(reservationId);
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Reservation is already cancelled");
        }
        if (!reservation.endTime().toInstant().isAfter(clock.instant())) {
            throw new ConflictException("Reservation has already ended");
        }
        return reservationRepository.cancel(reservationId)
                .orElseThrow(() -> new ConflictException("Reservation is already cancelled"));
    }

    private ParkingSpot requireSpot(long spotId) {
        return parkingSpotRepository.findById(spotId)
                .orElseThrow(() -> new NotFoundException("Parking spot not found"));
    }

    private Reservation requireReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
    }

    private void validateRequest(NewReservation request) {
        validateRange(request.startTime(), request.endTime(), "startTime must be before endTime");
        validateMinutePrecision(request.startTime(), request.endTime());
        Instant now = clock.instant();
        if (request.startTime().toInstant().isBefore(now)) {
            throw new InvalidRequestException("startTime must not be in the past");
        }
        if (Duration.between(request.startTime(), request.endTime()).compareTo(MAX_DURATION) > 0) {
            throw new InvalidRequestException("Reservation must not be longer than 24 hours");
        }
        if (request.startTime().toInstant().isAfter(now.plus(MAX_ADVANCE))) {
            throw new InvalidRequestException("startTime must not be more than 90 days ahead");
        }
    }

    private void validateRange(OffsetDateTime start, OffsetDateTime end, String message) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new InvalidRequestException(message);
        }
    }

    private void validateMinutePrecision(OffsetDateTime start, OffsetDateTime end) {
        if (start.getSecond() != 0 || start.getNano() != 0 || end.getSecond() != 0 || end.getNano() != 0) {
            throw new InvalidRequestException("Time values must have minute precision");
        }
    }
}

