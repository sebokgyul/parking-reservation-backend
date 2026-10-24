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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

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

    @MockitoSpyBean
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
    void databaseConstraintAllowsOnlyOneConcurrentDirectInsert() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T13:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-06-10T14:00:00+02:00");
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        Callable<Boolean> createReservation = () -> {
            startBarrier.await(5, TimeUnit.SECONDS);
            return reservationRepository.create(6L, "Concurrent", VehicleType.ELECTRIC, start, end).isPresent();
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
    void serviceReturnsConflictWhenBothRequestsPassTheOverlapCheck() throws Exception {
        CyclicBarrier afterCheck = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            afterCheck.await(5, TimeUnit.SECONDS);
            return result;
        }).when(reservationRepository).hasActiveOverlap(anyLong(), any(), any());

        Callable<Boolean> createReservation = () -> {
            try {
                reservationService.create(new NewReservation(1L, "Racer", VehicleType.STANDARD, T10, T11));
                return true;
            } catch (ConflictException exception) {
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
