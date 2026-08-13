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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

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
    void createRejectsBlankRequesterName() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "  ", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void createRejectsStartTimeInPast() {
        OffsetDateTime pastStart = OffsetDateTime.parse("2020-01-01T10:00:00+02:00");
        OffsetDateTime pastEnd = OffsetDateTime.parse("2020-01-01T11:00:00+02:00");
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, pastStart, pastEnd)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void createRejectsSecondPrecision() {
        OffsetDateTime startWithSeconds = OffsetDateTime.parse("2030-06-10T10:00:30+02:00");
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, startWithSeconds, END)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void createRejectsNonPositiveRange() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, START, START)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void createRejectsInactiveSpot() {
        when(parkingSpotRepository.findById(7L)).thenReturn(Optional.of(inactiveSpot(7L)));
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(7L, "Anna", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createRejectsStandardVehicleOnElectricSpot() {
        when(parkingSpotRepository.findById(5L)).thenReturn(Optional.of(electricSpot(5L)));
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(5L, "Anna", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createAllowsElectricVehicleOnElectricSpot() {
        when(parkingSpotRepository.findById(5L)).thenReturn(Optional.of(electricSpot(5L)));
        when(reservationRepository.hasActiveOverlap(5L, START, END)).thenReturn(false);
        Reservation created = new Reservation(1L, 5L, "Anna", VehicleType.ELECTRIC, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.create(5L, "Anna", VehicleType.ELECTRIC, START, END)).thenReturn(created);

        Reservation result = service.create(new CreateReservationRequest(5L, " Anna ", VehicleType.ELECTRIC, START, END));
        assertEquals(created, result);
    }

    @Test
    void createReturns409OnAppSideOverlap() {
        when(parkingSpotRepository.findById(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, END)).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.CONFLICT, ex.status());
        verify(reservationRepository, never()).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void createReturns409OnDbConstraintViolation() {
        when(parkingSpotRepository.findById(1L)).thenReturn(Optional.of(normalSpot(1L)));
        when(reservationRepository.hasActiveOverlap(1L, START, END)).thenReturn(false);
        when(reservationRepository.create(1L, "Anna", VehicleType.STANDARD, START, END))
                .thenThrow(new DataIntegrityViolationException("exclude constraint"));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.CONFLICT, ex.status());
    }

    @Test
    void createRejectsNonExistentSpot() {
        when(parkingSpotRepository.findById(999L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(new CreateReservationRequest(999L, "Anna", VehicleType.STANDARD, START, END)));
        assertEquals(HttpStatus.NOT_FOUND, ex.status());
    }

    @Test
    void cancelReturns404WhenReservationMissing() {
        when(reservationRepository.findById(42L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(42L));
        assertEquals(HttpStatus.NOT_FOUND, ex.status());
    }

    @Test
    void cancelReturns409WhenAlreadyCancelled() {
        Reservation cancelled = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(cancelled));
        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(1L));
        assertEquals(HttpStatus.CONFLICT, ex.status());
    }

    @Test
    void cancelReturns409WhenRaceConditionEmptiesUpdate() {
        Reservation active = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(active));
        when(reservationRepository.cancel(1L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(1L));
        assertEquals(HttpStatus.CONFLICT, ex.status());
    }

    @Test
    void cancelSucceedsForActiveReservation() {
        Reservation active = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        Reservation cancelled = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(active));
        when(reservationRepository.cancel(1L)).thenReturn(Optional.of(cancelled));
        Reservation result = service.cancel(1L);
        assertEquals(ReservationStatus.CANCELLED, result.status());
    }

    @Test
    void findReservationsRejectsInvertedRange() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.findReservations(1L, END, START));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void findReservationsRejectsNonExistentSpot() {
        when(parkingSpotRepository.findById(999L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () ->
                service.findReservations(999L, START, END));
        assertEquals(HttpStatus.NOT_FOUND, ex.status());
    }

    @Test
    void findReservationsReturnsOverlappingActiveOnes() {
        when(parkingSpotRepository.findById(1L)).thenReturn(Optional.of(normalSpot(1L)));
        Reservation r = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD, START, END,
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationRepository.findActiveOverlapping(1L, START, END)).thenReturn(List.of(r));
        List<Reservation> result = service.findReservations(1L, START, END);
        assertEquals(1, result.size());
    }
}
