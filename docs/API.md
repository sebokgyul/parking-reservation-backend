# API

JSON in and out. Timestamps are ISO-8601 with an offset, e.g. `2030-06-10T10:00:00+02:00`. Responses always use UTC (`Z`), whatever offset the request had. Reservation times have to be whole minutes (no seconds or fractions).

## Parking spot

```json
{ "id": 1, "code": "A-01", "spotType": "NORMAL", "active": true }
```

`spotType` is `NORMAL` or `ELECTRIC`. Inactive spots can't be booked.

## Reservation

```json
{
  "id": "3f6d0c1e-8a5b-4c2f-9e47-1b2a6d9c0e53",
  "parkingSpotId": 1,
  "requesterName": "Anna Kovacs",
  "vehicleType": "STANDARD",
  "startTime": "2030-06-10T08:00:00Z",
  "endTime": "2030-06-10T09:00:00Z",
  "status": "ACTIVE",
  "createdAt": "2030-06-01T08:00:00Z"
}
```

`vehicleType` is `STANDARD` or `ELECTRIC`, `status` is `ACTIVE` or `CANCELLED`. The `id` is a random UUID and works as the booking code: it is all you need to look up or cancel the reservation, and the spot listing doesn't show it.

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

Every field is required. `requesterName` can't be blank, longer than 100 characters or contain control characters, and is stored trimmed. `startTime` must be before `endTime`, not in the past and at most 90 days ahead. A reservation is at most 24 hours long.

- `201` with the created reservation and `Location: /reservations/{id}`
- `400` missing or invalid field. The message names the field, e.g. `parkingSpotId must not be null`
- `404` spot doesn't exist
- `409` spot is inactive, a standard vehicle on an electric spot, or the time overlaps an active reservation. Overlap is checked on `[startTime, endTime)`, so back-to-back bookings are fine.

## GET /reservations/{reservationId}

- `200` with the reservation
- `400` the id isn't a UUID
- `404` no such reservation

## GET /parking-spots/{spotId}/reservations?from=...&to=...

The occupied intervals on the spot that overlap the window (`startTime < to` and `endTime > from`), ordered by start time. Only the times are returned, no id or name.

```json
[{ "startTime": "2030-06-10T08:00:00Z", "endTime": "2030-06-10T09:00:00Z" }]
```

`from` and `to` are required and `from` has to be before `to`. Seconds are allowed here. In a query string `+` means a space, so write the offset as `%2B02:00` or use `Z`, e.g. `?from=2030-06-10T00:00:00Z&to=2030-06-11T00:00:00Z`.

- `200` with an array, possibly empty
- `400` missing, malformed or inverted window
- `404` spot doesn't exist

## POST /reservations/{reservationId}/cancel

No body. Sets the reservation to `CANCELLED`. A reservation that is already running can still be cancelled.

- `200` with the cancelled reservation
- `400` the id isn't a UUID
- `404` no such reservation
- `409` already cancelled, or the reservation has already ended. Already cancelled is also what the losing request gets when two cancels race.

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

An unknown path gives `404`, a wrong HTTP method gives `405`, and a non-JSON body on `POST /reservations` gives `415`. An `Accept` header that rules out JSON gives `406` with an empty body, before anything is created.

`GET /actuator/health` is exposed as well, for health checks.
