# parking-reservation-backend

Small REST API for booking parking spots. Java 21, Spring Boot 3, Postgres, plain JdbcTemplate.

The main thing it has to get right is that two active reservations for the same spot can't overlap. Checking for an overlap in the service and then inserting is racy, so the rule lives in Postgres as an exclusion constraint:

```sql
EXCLUDE USING gist (
    parking_spot_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
) WHERE (status = 'ACTIVE')
```

Ranges are half-open, so 10:00-11:00 and 11:00-12:00 don't clash. Cancelling only flips the status to `CANCELLED`, which keeps the row but frees the slot. The service still runs its own overlap query first so the usual case gets a readable 409. Before that query it locks the spot row with `SELECT ... FOR UPDATE`, so bookings for the same spot run one after another and the query sees every committed reservation. Without the lock, two overlapping inserts can deadlock inside the constraint check, and Postgres takes a second to notice. The constraint still catches anything that bypasses the service, and `ReservationDatabaseIntegrationTest` sends parallel requests for the same slot to check that exactly one wins.

Other rules: electric spots only take electric vehicles, inactive spots can't be booked, times are whole minutes, the start can't be in the past or more than 90 days ahead, a reservation is at most 24 hours long, and one that has already ended can't be cancelled.

There are no user accounts. A reservation's id is a random UUID that works as its booking code: the client gets it in the 201 response and needs it to look up or cancel the reservation. The spot listing only shows the occupied intervals, not who booked them or their ids.

## Running

```bash
docker compose up --build -d
```

The API listens on `localhost:8080`. Flyway creates the schema and seeds seven spots: `A-01` to `A-04`, the electric `B-01` and `B-02`, and `C-01`, which is inactive. Logs are in `docker compose logs -f backend`, and `docker compose down -v` drops the database volume.

## Tests

Needs JDK 21 and a running Docker daemon, because the integration tests start Postgres with Testcontainers.

```bash
./mvnw test
```

## API

```
GET  /parking-spots
POST /reservations
GET  /reservations/{reservationId}
GET  /parking-spots/{spotId}/reservations?from=...&to=...
POST /reservations/{reservationId}/cancel
```

Request and response formats and the error codes are in [docs/API.md](docs/API.md).
