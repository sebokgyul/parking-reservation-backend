package hu.parking.reservation.api;

import hu.parking.reservation.domain.OccupiedInterval;
import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.SpotType;
import hu.parking.reservation.domain.VehicleType;
import hu.parking.reservation.service.ConflictException;
import hu.parking.reservation.service.NotFoundException;
import hu.parking.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParkingController.class)
@ExtendWith(OutputCaptureExtension.class)
class ParkingControllerWebMvcTest {

    private static final UUID ID = UUID.fromString("6f1c2a52-3d4e-4b8a-9c71-2f5e8d9a0b13");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
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
        Reservation reservation = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD,
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
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void createReservationReturns409OnConflict() throws Exception {
        when(reservationService.create(any()))
                .thenThrow(new ConflictException("overlap"));

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request value"));
    }

    @Test
    void createReservationReturns400WithMissingFieldNames() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endTime must not be null; parkingSpotId must not be null; "
                        + "requesterName must not be blank; startTime must not be null; vehicleType must not be null"));
        verify(reservationService, never()).create(any());
    }

    @Test
    void createReservationRejectsBlankRequesterName() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": 1,
                                  "requesterName": "  ",
                                  "vehicleType": "STANDARD",
                                  "startTime": "2030-06-10T10:00:00+02:00",
                                  "endTime": "2030-06-10T11:00:00+02:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requesterName must not be blank"));
        verify(reservationService, never()).create(any());
    }

    @Test
    void createReservationRejectsControlCharactersInName() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": 1,
                                  "requesterName": "An\\u0000na",
                                  "vehicleType": "STANDARD",
                                  "startTime": "2030-06-10T10:00:00+02:00",
                                  "endTime": "2030-06-10T11:00:00+02:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requesterName must not contain control characters"));
        verify(reservationService, never()).create(any());
    }

    @Test
    void createReservationNamesTheFieldWithInvalidEnumValue() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": 1,
                                  "requesterName": "Anna",
                                  "vehicleType": "electric",
                                  "startTime": "2030-06-10T10:00:00+02:00",
                                  "endTime": "2030-06-10T11:00:00+02:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for field 'vehicleType'"));
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
    void listReservationsReturnsOnlyIntervals() throws Exception {
        when(reservationService.findReservations(anyLong(), any(), any())).thenReturn(List.of(new OccupiedInterval(
                OffsetDateTime.parse("2030-06-10T08:00:00Z"), OffsetDateTime.parse("2030-06-10T09:00:00Z"))));

        mockMvc.perform(get("/parking-spots/1/reservations")
                        .param("from", "2030-06-10T00:00:00Z")
                        .param("to", "2030-06-11T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("2030-06-10T08:00:00Z"))
                .andExpect(jsonPath("$[0].endTime").value("2030-06-10T09:00:00Z"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].requesterName").doesNotExist());
    }

    @Test
    void listReservationsReturns400WhenMissingTimeParams() throws Exception {
        mockMvc.perform(get("/parking-spots/1/reservations"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelReservationReturns200() throws Exception {
        Reservation cancelled = new Reservation(ID, 1L, "Anna", VehicleType.STANDARD,
                OffsetDateTime.parse("2030-06-10T10:00:00+02:00"),
                OffsetDateTime.parse("2030-06-10T11:00:00+02:00"),
                ReservationStatus.CANCELLED, OffsetDateTime.parse("2030-06-10T08:00:00Z"));
        when(reservationService.cancel(any())).thenReturn(cancelled);

        mockMvc.perform(post("/reservations/" + ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelReservationReturns404ForMissing() throws Exception {
        when(reservationService.cancel(any()))
                .thenThrow(new NotFoundException("Reservation not found"));

        mockMvc.perform(post("/reservations/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reservation not found"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/does-not-exist"));
    }

    @Test
    void unexpectedErrorReturns500AndIsLogged(CapturedOutput output) throws Exception {
        when(reservationService.listParkingSpots()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/parking-spots"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"));

        assertTrue(output.getAll().contains("Unhandled exception on GET /parking-spots"));
    }

    @Test
    void unsupportedAcceptTypeReturns406() throws Exception {
        mockMvc.perform(get("/parking-spots").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void cancelReservationReturns400ForMalformedId() throws Exception {
        mockMvc.perform(post("/reservations/123/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request value"));
        verify(reservationService, never()).cancel(any());
    }
}
