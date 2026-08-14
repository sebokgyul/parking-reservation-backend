package hu.parking.reservation;

import hu.parking.reservation.api.ApiException;
import hu.parking.reservation.api.CreateReservationRequest;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.repository.ReservationRepository;
import hu.parking.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class ReservationDatabaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationRepository reservationRepository;

    private static final OffsetDateTime T10 = OffsetDateTime.parse("2030-06-10T10:00:00+02:00");
    private static final OffsetDateTime T11 = OffsetDateTime.parse("2030-06-10T11:00:00+02:00");
    private static final OffsetDateTime T12 = OffsetDateTime.parse("2030-06-10T12:00:00+02:00");

    @Test
    void serviceRejectsSequentialOverlappingReservations() {
        reservationService.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD, T10, T11));

        ApiException exception = assertThrows(ApiException.class, () ->
                reservationService.create(new CreateReservationRequest(1L, "Bela", VehicleType.STANDARD,
                        OffsetDateTime.parse("2030-06-10T10:30:00+02:00"),
                        OffsetDateTime.parse("2030-06-10T11:30:00+02:00"))));

        assertEquals(409, exception.status().value());
    }

    @Test
    void databaseConstraintAllowsOnlyOneConcurrentDirectInsert() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T13:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-06-10T14:00:00+02:00");
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        Callable<Boolean> createReservation = () -> {
            startBarrier.await(5, TimeUnit.SECONDS);
            try {
                reservationRepository.create(6L, "Concurrent", VehicleType.ELECTRIC, start, end);
                return true;
            } catch (DataIntegrityViolationException exception) {
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(createReservation);
            Future<Boolean> second = executor.submit(createReservation);

            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adjacentReservationsDoNotOverlap() {
        reservationService.create(new CreateReservationRequest(2L, "Anna", VehicleType.STANDARD, T10, T11));

        Reservation second = reservationService.create(new CreateReservationRequest(2L, "Bela", VehicleType.STANDARD, T11, T12));

        assertEquals(ReservationStatus.ACTIVE, second.status());
    }

    @Test
    void cancelledReservationFreesUpTheSlot() {
        Reservation first = reservationService.create(new CreateReservationRequest(3L, "Anna", VehicleType.STANDARD, T10, T11));
        reservationService.cancel(first.id());

        Reservation rebooked = reservationService.create(new CreateReservationRequest(3L, "Bela", VehicleType.STANDARD, T10, T11));

        assertEquals(ReservationStatus.ACTIVE, rebooked.status());
    }

    @Test
    void electricSpotRejectsStandardVehicle() {
        ApiException exception = assertThrows(ApiException.class, () ->
                reservationService.create(new CreateReservationRequest(5L, "Anna", VehicleType.STANDARD, T10, T11)));

        assertEquals(409, exception.status().value());
    }

    @Test
    void electricSpotAcceptsElectricVehicle() {
        Reservation reservation = reservationService.create(new CreateReservationRequest(6L, "Anna", VehicleType.ELECTRIC, T10, T11));

        assertEquals(ReservationStatus.ACTIVE, reservation.status());
    }

    @Test
    void inactiveSpotRejectsReservation() {
        ApiException exception = assertThrows(ApiException.class, () ->
                reservationService.create(new CreateReservationRequest(7L, "Anna", VehicleType.STANDARD, T10, T11)));

        assertEquals(409, exception.status().value());
    }

    @Test
    void findReservationsReturnsOnlyActiveOverlappingOnes() {
        Reservation r1 = reservationService.create(new CreateReservationRequest(4L, "Anna", VehicleType.STANDARD, T10, T11));
        reservationService.cancel(r1.id());
        reservationService.create(new CreateReservationRequest(4L, "Bela", VehicleType.STANDARD, T11, T12));

        List<Reservation> result = reservationService.findReservations(4L, T10, T12);

        assertEquals(1, result.size());
        assertEquals("Bela", result.get(0).requesterName());
        assertEquals(ReservationStatus.ACTIVE, result.get(0).status());
    }

    @Test
    void cancelReturns404ForMissingReservation() {
        ApiException exception = assertThrows(ApiException.class, () -> reservationService.cancel(999999L));

        assertEquals(404, exception.status().value());
    }
}
