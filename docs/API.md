# API

JSON in and out. Timestamps are ISO-8601 with an offset, e.g. `2030-06-10T10:00:00+02:00`, and have to be whole minutes (no seconds or fractions).

## Parking spot

```json
{ "id": 1, "code": "A-01", "spotType": "NORMAL", "active": true }
```

`spotType` is `NORMAL` or `ELECTRIC`. Inactive spots can't be booked.

## Reservation

```json
{
  "id": 12,
  "parkingSpotId": 1,
  "requesterName": "Anna Kovacs",
  "vehicleType": "STANDARD",
  "startTime": "2030-06-10T10:00:00+02:00",
  "endTime": "2030-06-10T11:00:00+02:00",
  "status": "ACTIVE",
  "createdAt": "2030-06-01T08:00:00Z"
}
```

`vehicleType` is `STANDARD` or `ELECTRIC`, `status` is `ACTIVE` or `CANCELLED`.

## GET /parking-spots

All spots ordered by code, inactive ones included. Returns `200`.

## POST /reservations

Needs `Content-Type: application/json`.

```json
{
  "parkingSpotId": 1,
  "requesterName": "Anna Kovacs",
  "vehicleType": "STANDARD",
  "startTime": "2030-06-10T10:00:00+02:00",
  "endTime": "2030-06-10T11:00:00+02:00"
}
```

Every field is required. `requesterName` is trimmed and must be 1-100 characters. `startTime` must be before `endTime` and not in the past.

- `201` with the created reservation
- `400` missing or invalid field
- `404` spot doesn't exist
- `409` spot is inactive, a standard vehicle on an electric spot, or the time overlaps an active reservation. Overlap is checked on `[startTime, endTime)`, so back-to-back bookings are fine.

## GET /parking-spots/{spotId}/reservations?from=...&to=...

Active reservations on the spot that overlap the window (`startTime < to` and `endTime > from`), ordered by start time. `from` and `to` are required, same format as above, and `from` has to be before `to`.

- `200` with an array, possibly empty
- `400` missing, malformed or inverted window
- `404` spot doesn't exist

## POST /reservations/{reservationId}/cancel

No body. Sets the reservation to `CANCELLED`.

- `200` with the cancelled reservation
- `404` no such reservation
- `409` already cancelled. This is also what the losing request gets when two cancels race.

## Errors

Every handled error has the same shape:

```json
{
  "timestamp": "2030-06-01T08:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Parking spot is already reserved for the requested time interval",
  "path": "/reservations"
}
```

A wrong HTTP method gives `405`, and a non-JSON body on `POST /reservations` gives `415`.

`GET /actuator/health` is exposed as well, for health checks.
