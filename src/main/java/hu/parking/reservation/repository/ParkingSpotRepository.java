package hu.parking.reservation.repository;

import hu.parking.reservation.domain.ParkingSpot;
import hu.parking.reservation.domain.SpotType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ParkingSpotRepository {
    private final JdbcTemplate jdbcTemplate;

    public ParkingSpotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ParkingSpot> findAll() {
        return jdbcTemplate.query("SELECT id, code, spot_type, is_active FROM parking_spots ORDER BY code", this::mapSpot);
    }

    public Optional<ParkingSpot> findById(long id) {
        return jdbcTemplate.query("SELECT id, code, spot_type, is_active FROM parking_spots WHERE id = ?", this::mapSpot, id)
                .stream().findFirst();
    }

    private ParkingSpot mapSpot(ResultSet rs, int rowNum) throws SQLException {
        return new ParkingSpot(rs.getLong("id"), rs.getString("code"), SpotType.valueOf(rs.getString("spot_type")), rs.getBoolean("is_active"));
    }
}
