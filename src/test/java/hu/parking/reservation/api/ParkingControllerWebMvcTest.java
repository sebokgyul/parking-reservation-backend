package hu.parking.reservation.api;

import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.SpotType;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParkingController.class)
class ParkingControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ReservationService reservationService;

    @Test
    void listParkingSpotsReturnsJsonArray() throws Exception {
        when(reservationService.listParkingSpots()).thenReturn(List.of(
                new ParkingSpot(1L, "A-01", SpotType.NORMAL, true)));

        mockMvc.perform(get("/parking-spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("A-01"));
    }

    @Test
    void createReservationReturns201() throws Exception {
        Reservation reservation = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T10:00:00+02:00"),
                OffsetDateTime.parse("2030-06-10T11:00:00+02:00"),
                ReservationStatus.ACTIVE, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationService.create(any())).thenReturn(reservation);

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": 1,
                                  "requesterName": "Anna",
                                  "vehicleType": "STANDARD",
                                  "startTime": "2030-06-10T10:00:00+02:00",
                                  "endTime": "2030-06-10T11:00:00+02:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createReservationReturns409OnConflict() throws Exception {
        when(reservationService.create(any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "overlap"));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": 1,
                                  "requesterName": "Anna",
                                  "vehicleType": "STANDARD",
                                  "startTime": "2030-06-10T10:00:00+02:00",
                                  "endTime": "2030-06-10T11:00:00+02:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("overlap"))
                .andExpect(jsonPath("$.path").value("/reservations"));
    }

    @Test
    void createReservationReturns400OnMalformedBody() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReservationReturns415ForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content-Type must be application/json"));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(post("/parking-spots"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method is not supported for this endpoint"));
    }

    @Test
    void listReservationsReturns400WhenMissingTimeParams() throws Exception {
        mockMvc.perform(get("/parking-spots/1/reservations"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelReservationReturns200() throws Exception {
        Reservation cancelled = new Reservation(1L, 1L, "Anna", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T10:00:00+02:00"),
                OffsetDateTime.parse("2030-06-10T11:00:00+02:00"),
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationService.cancel(anyLong())).thenReturn(cancelled);

        mockMvc.perform(post("/reservations/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelReservationReturns404ForMissing() throws Exception {
        when(reservationService.cancel(anyLong()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Reservation not found"));

        mockMvc.perform(post("/reservations/999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reservation not found"));
    }
}
