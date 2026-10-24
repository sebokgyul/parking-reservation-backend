package hu.parking.reservation.service;

import hu.parking.reservation.domain.OccupiedInterval;
import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.SpotType;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.repository.ParkingSpotRepository;
import hu.parking.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID ID = UUID.fromString("6f1c2a52-3d4e-4b8a-9c71-2f5e8d9a0b13");
    private static final Instant NOW = Instant.parse("2030-06-10T08:00:00Z");
    private static final OffsetDateTime START = OffsetDateTime.parse("2030-06-10T10:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2030-06-10T11:00:00+02:00");

    @Mock
    ParkingSpotRepository parkingSpotRepository;

    @Mock
    ReservationRepository reservationRepository;

    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(parkingSpotRepository, reservationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ParkingSpot normalSpot(long id) {
        return new ParkingSpot(id, "A-01", SpotType.NORMAL, true);
    }

    private ParkingSpot electricSpot(long id) {
        return new ParkingSpot(id, "B-01", SpotType.ELECTRIC, true);
    }

    private ParkingSpot inactiveSpot(long id) {
        return new ParkingSpot(id, "C-01", SpotType.NORMAL, false);
    }

    @Test
    void createRejectsStartTimeInPast() {
        OffsetDateTime pastStart = OffsetDateTime.parse("2020-01-01T10:00:00+02:00");
        OffsetDateTime pastEnd = OffsetDateTime.parse("2020-01-01T11:00:00+02:00");
        assertThrows(InvalidRequestException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, pastStart, pastEnd)));
    }

    @Test
    void createRejectsSecondPrecision() {
        OffsetDateTime startWithSeconds = OffsetDateTime.parse("2030-06-10T10:00:30+02:00");
        assertThrows(InvalidRequestException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, startWithSeconds, END)));
    }

    @Test
    void createRejectsReservationLongerThan24Hours() {
        OffsetDateTime end = START.plusHours(24).plusMinutes(1);
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, end)));
        assertEquals("Reservation must not be longer than 24 hours", exception.getMessage());
    }

    @Test
    void createAllowsExactly24Hours() {
        OffsetDateTime end = START.plusHours(24);
        when(parkingSpotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, end)).thenReturn(false);
        Reservation created = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, START, end,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.create(1L, "Anna", VehicleType.STANDARD, START, end)).thenReturn(Optional.of(created));

        assertEquals(created, service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, end)));
    }

    @Test
    void createRejectsStartMoreThan90DaysAhead() {
        OffsetDateTime start = NOW.plus(Duration.ofDays(90)).plus(Duration.ofMinutes(1)).atOffset(ZoneOffset.UTC);
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, start, start.plusHours(1))));
        assertEquals("startTime must not be more than 90 days ahead", exception.getMessage());
    }

    @Test
    void createRejectsNonPositiveRange() {
        assertThrows(InvalidRequestException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, START)));
    }

    @Test
    void createRejectsInactiveSpot() {
        when(parkingSpotRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(inactiveSpot(7L)));
        assertThrows(ConflictException.class, () ->
                service.create(new NewReservation(7L, "Anna", VehicleType.STANDARD, START, END)));
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createRejectsStandardVehicleOnElectricSpot() {
        when(parkingSpotRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(electricSpot(5L)));
        assertThrows(ConflictException.class, () ->
                service.create(new NewReservation(5L, "Anna", VehicleType.STANDARD, START, END)));
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createAllowsElectricVehicleOnElectricSpot() {
        when(parkingSpotRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(electricSpot(5L)));
        when(reservationRepository.hasActiveOverlap(5L, START, END)).thenReturn(false);
        Reservation created = new Reservation(ID, 5L, "Anna", VehicleType.ELECTRIC, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.create(5L, "Anna", VehicleType.ELECTRIC, START, END)).thenReturn(Optional.of(created));

        Reservation result = service.create(new NewReservation(5L, " Anna ", VehicleType.ELECTRIC, START, END));
        assertEquals(created, result);
    }

    @Test
    void createReturns409OnAppSideOverlap() {
        when(parkingSpotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, END)).thenReturn(true);
        assertThrows(ConflictException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, END)));
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createReturns409WhenOverlapConstraintRejectsInsert() {
        when(parkingSpotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, END)).thenReturn(false);
        when(reservationRepository.create(1L, "Anna", VehicleType.STANDARD, START, END)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, END)));
    }

    @Test
    void createPassesOtherIntegrityErrorsThrough() {
        when(parkingSpotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, END)).thenReturn(false);
        when(reservationRepository.create(1L, "Anna", VehicleType.STANDARD, START, END))
                .thenThrow(new DataIntegrityViolationException("invalid byte sequence"));

        assertThrows(DataIntegrityViolationException.class, () ->
                service.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, START, END)));
    }

    @Test
    void createRejectsNonExistentSpot() {
        when(parkingSpotRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () ->
                service.create(new NewReservation(999L, "Anna", VehicleType.STANDARD, START, END)));
    }

    @Test
    void cancelReturns404WhenReservationMissing() {
        when(reservationRepository.findById(ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.cancel(ID));
    }

    @Test
    void cancelReturns409WhenAlreadyCancelled() {
        Reservation cancelled = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(ID)).thenReturn(Optional.of(cancelled));
        assertThrows(ConflictException.class, () -> service.cancel(ID));
    }

    @Test
    void cancelReturns409WhenRaceConditionEmptiesUpdate() {
        Reservation active = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(ID)).thenReturn(Optional.of(active));
        when(reservationRepository.cancel(ID)).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.cancel(ID));
    }

    @Test
    void cancelReturns409WhenReservationHasEnded() {
        Reservation ended = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T06:00:00Z"), OffsetDateTime.parse("2030-06-10T07:00:00Z"),
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-09T08:00:00Z"));
        when(reservationRepository.findById(ID)).thenReturn(Optional.of(ended));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.cancel(ID));
        assertEquals("Reservation has already ended", exception.getMessage());
        verify(reservationRepository, never()).cancel(any());
    }

    @Test
    void cancelSucceedsForRunningReservation() {
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T07:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2030-06-10T09:00:00Z");
        Reservation running = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, start, end,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-09T08:00:00Z"));
        Reservation cancelled = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, start, end,
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-09T08:00:00Z"));
        when(reservationRepository.findById(ID)).thenReturn(Optional.of(running));
        when(reservationRepository.cancel(ID)).thenReturn(Optional.of(cancelled));

        assertEquals(ReservationStatus.CANCELLED, service.cancel(ID).status());
    }

    @Test
    void cancelSucceedsForActiveReservation() {
        Reservation active = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        Reservation cancelled = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(ID)).thenReturn(Optional.of(active));
        when(reservationRepository.cancel(ID)).thenReturn(Optional.of(cancelled));
        Reservation result = service.cancel(ID);
        assertEquals(ReservationStatus.CANCELLED, result.status());
    }

    @Test
    void findReservationsRejectsInvertedRange() {
        assertThrows(InvalidRequestException.class, () ->
                service.findReservations(1L, END, START));
    }

    @Test
    void findReservationsRejectsNonExistentSpot() {
        when(parkingSpotRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () ->
                service.findReservations(999L, START, END));
    }

    @Test
    void findReservationsAcceptsSecondPrecisionWindow() {
        OffsetDateTime from = OffsetDateTime.parse("2030-06-10T10:00:30+02:00");
        when(parkingSpotRepository.findById(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.findActiveIntervals(1L, from, END)).thenReturn(List.of());

        assertEquals(List.of(), service.findReservations(1L, from, END));
    }

    @Test
    void findReservationReturns404WhenMissing() {
        when(reservationRepository.findById(ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.findReservation(ID));
    }

    @Test
    void findReservationsReturnsOverlappingActiveOnes() {
        when(parkingSpotRepository.findById(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.findActiveIntervals(1L, START, END)).thenReturn(List.of(new OccupiedInterval(START, END)));
        List<OccupiedInterval> result = service.findReservations(1L, START, END);
        assertEquals(1, result.size());
    }
}
