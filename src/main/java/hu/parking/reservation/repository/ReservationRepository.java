package hu.parking.reservation.repository;

import hu.parking.reservation.domain.Reservation;
import hu.parking.reservation.domain.ReservationStatus;
import hu.parking.reservation.domain.VehicleType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReservationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasActiveOverlap(long parkingSpotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reservations
                WHERE parking_spot_id = ? AND status = 'ACTIVE'
                  AND start_time < ? AND end_time > ?
                """, Integer.class, parkingSpotId, endTime, startTime);
        return count != null && count > 0;
    }

    public Reservation create(long parkingSpotId, String requesterName, VehicleType vehicleType,
                              OffsetDateTime startTime, OffsetDateTime endTime) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reservations (parking_spot_id, requester_name, vehicle_type, start_time, end_time)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, parking_spot_id, requester_name, vehicle_type, start_time, end_time, status, created_at
                """, this::mapReservation, parkingSpotId, requesterName, vehicleType.name(), startTime, endTime);
    }

    public List<Reservation> findActiveOverlapping(long parkingSpotId, OffsetDateTime from, OffsetDateTime to) {
        return jdbcTemplate.query("""
                SELECT id, parking_spot_id, requester_name, vehicle_type, start_time, end_time, status, created_at
                FROM reservations
                WHERE parking_spot_id = ? AND status = 'ACTIVE'
                  AND start_time < ? AND end_time > ?
                ORDER BY start_time
                """, this::mapReservation, parkingSpotId, to, from);
    }

    public Optional<Reservation> findById(long reservationId) {
        return jdbcTemplate.query("""
                SELECT id, parking_spot_id, requester_name, vehicle_type, start_time, end_time, status, created_at
                FROM reservations WHERE id = ?
                """, this::mapReservation, reservationId).stream().findFirst();
    }

    public Optional<Reservation> cancel(long reservationId) {
        return jdbcTemplate.query("""
                UPDATE reservations SET status = 'CANCELLED'
                WHERE id = ? AND status = 'ACTIVE'
                RETURNING id, parking_spot_id, requester_name, vehicle_type, start_time, end_time, status, created_at
                """, this::mapReservation, reservationId).stream().findFirst();
    }

    private Reservation mapReservation(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Reservation(
                rs.getLong("id"), rs.getLong("parking_spot_id"), rs.getString("requester_name"),
                VehicleType.valueOf(rs.getString("vehicle_type")),
                rs.getObject("start_time", OffsetDateTime.class), rs.getObject("end_time", OffsetDateTime.class),
                ReservationStatus.valueOf(rs.getString("status")), rs.getObject("created_at", OffsetDateTime.class));
    }
}

