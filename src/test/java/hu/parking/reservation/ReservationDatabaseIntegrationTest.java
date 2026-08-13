package hu.parking.reservation;

import hu.parking.reservation.api.ApiException;
import hu.parking.reservation.api.CreateReservationRequest;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void databaseConstraintRejectsOverlappingActiveReservations() {
        reservationService.create(new CreateReservationRequest(1L, "Anna", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T10:00:00+02:00"), OffsetDateTime.parse("2030-06-10T11:00:00+02:00")));

        ApiException exception = assertThrows(ApiException.class, () -> reservationService.create(new CreateReservationRequest(1L, "Bela", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T10:30:00+02:00"), OffsetDateTime.parse("2030-06-10T11:30:00+02:00"))));

        assertEquals(409, exception.status().value());
    }
}
