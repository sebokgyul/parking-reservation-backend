package hu.parking.reservation.domain;

public record ParkingSpot(long id, String code, SpotType spotType, boolean active) {
}
