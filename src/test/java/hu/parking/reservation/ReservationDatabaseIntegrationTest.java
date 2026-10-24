package hu.parking.reservation;

import hu.parking.reservation.domain.OccupiedInterval;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.repository.ReservationRepository;
import hu.parking.reservation.service.ConflictException;
import hu.parking.reservation.service.NewReservation;
import hu.parking.reservation.service.NotFoundException;
import hu.parking.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final OffsetDateTime T10 = OffsetDateTime.parse("2030-06-10T10:00:00+02:00");
    private static final OffsetDateTime T11 = OffsetDateTime.parse("2030-06-10T11:00:00+02:00");
    private static final OffsetDateTime T12 = OffsetDateTime.parse("2030-06-10T12:00:00+02:00");

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM reservations");
    }

    @Test
    void serviceRejectsSequentialOverlappingReservations() {
        reservationService.create(new NewReservation(1L, "Anna", VehicleType.STANDARD, T10, T11));

        assertThrows(ConflictException.class, () ->
                reservationService.create(new NewReservation(1L, "Bela", VehicleType.STANDARD,
                        OffsetDateTime.parse("2030-06-10T10:30:00+02:00"),
                        OffsetDateTime.parse("2030-06-10T11:30:00+02:00"))));
    }

    @Test
    void databaseConstraintRejectsOverlapThatSkipsTheServiceCheck() {
        reservationRepository.create(6L, "Anna", VehicleType.ELECTRIC, T10, T11);

        assertTrue(reservationRepository.create(6L, "Bela", VehicleType.ELECTRIC,
                OffsetDateTime.parse("2030-06-10T10:30:00+02:00"),
                OffsetDateTime.parse("2030-06-10T11:30:00+02:00")).isEmpty());
    }

    @Test
    void concurrentRequestsForTheSameSlotGetOneReservation() throws Exception {
        int requests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            for (int round = 0; round < 5; round++) {
                OffsetDateTime start = T10.plusHours(round);
                CyclicBarrier startBarrier = new CyclicBarrier(requests);

                Callable<Boolean> createReservation = () -> {
                    startBarrier.await(5, TimeUnit.SECONDS);
                    try {
                        reservationService.create(new NewReservation(1L, "Racer", VehicleType.STANDARD, start, start.plusHours(1)));
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                };

                int successes = 0;
                for (Future<Boolean> result : executor.invokeAll(Collections.nCopies(requests, createReservation), 10, TimeUnit.SECONDS)) {
                    successes += result.get() ? 1 : 0;
                }

                assertEquals(1, successes);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void otherIntegrityErrorIsNotReportedAsConflict() {
        assertThrows(DataIntegrityViolationException.class, () ->
                reservationService.create(new NewReservation(1L, "An\u0000na", VehicleType.STANDARD, T10, T11)));
    }

    @Test
    void databaseRejectsReservationLongerThan24Hours() {
        assertThrows(DataIntegrityViolationException.class, () ->
                reservationRepository.create(1L, "Anna", VehicleType.STANDARD, T10, T10.plusHours(25)));
    }

    @Test
    void adjacentReservationsDoNotOverlap() {
        reservationService.create(new NewReservation(2L, "Anna", VehicleType.STANDARD, T10, T11));

        Reservation second = reservationService.create(new NewReservation(2L, "Bela", VehicleType.STANDARD, T11, T12));

        assertEquals(ReservationStatus.ACTIVE, second.status());
    }

    @Test
    void cancelledReservationFreesUpTheSlot() {
        Reservation first = reservationService.create(new NewReservation(3L, "Anna", VehicleType.STANDARD, T10, T11));
        reservationService.cancel(first.id());

        Reservation rebooked = reservationService.create(new NewReservation(3L, "Bela", VehicleType.STANDARD, T10, T11));

        assertEquals(ReservationStatus.ACTIVE, rebooked.status());
    }

    @Test
    void electricSpotRejectsStandardVehicle() {
        assertThrows(ConflictException.class, () ->
                reservationService.create(new NewReservation(5L, "Anna", VehicleType.STANDARD, T10, T11)));
    }

    @Test
    void electricSpotAcceptsElectricVehicle() {
        Reservation reservation = reservationService.create(new NewReservation(6L, "Anna", VehicleType.ELECTRIC, T10, T11));

        assertEquals(ReservationStatus.ACTIVE, reservation.status());
    }

    @Test
    void inactiveSpotRejectsReservation() {
        assertThrows(ConflictException.class, () ->
                reservationService.create(new NewReservation(7L, "Anna", VehicleType.STANDARD, T10, T11)));
    }

    @Test
    void findReservationsReturnsOnlyActiveOverlappingOnes() {
        Reservation r1 = reservationService.create(new NewReservation(4L, "Anna", VehicleType.STANDARD, T10, T11));
        reservationService.cancel(r1.id());
        reservationService.create(new NewReservation(4L, "Bela", VehicleType.STANDARD, T11, T12));

        List<OccupiedInterval> result = reservationService.findReservations(4L, T10, T12);

        assertEquals(1, result.size());
        assertTrue(T11.isEqual(result.get(0).startTime()));
        assertTrue(T12.isEqual(result.get(0).endTime()));
    }

    @Test
    void cancelReturns404ForMissingReservation() {
        assertThrows(NotFoundException.class, () -> reservationService.cancel(UUID.randomUUID()));
    }
}
