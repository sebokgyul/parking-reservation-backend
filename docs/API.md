# API-leírás

Minden időpont ISO-8601 offsettel küldendő, például `2030-06-10T10:00:00+02:00`. A rendszer perc pontosságot vár.

## `GET /parking-spots`

A seedelt parkolóhelyek listája. `200 OK`.

## `POST /reservations`

Foglalás létrehozása. Minta törzs:

```json docs/API.md
{
  "parkingSpotId": 1,
  "requesterName": "Anna Kovacs",
  "vehicleType": "STANDARD",
  "startTime": "2030-06-10T10:00:00+02:00",
  "endTime": "2030-06-10T11:00:00+02:00"
}
```

Siker: `201 Created`. Hibák: `400` érvénytelen adat, `404` nem létező hely, `409` inaktív hely, átfedés vagy elektromos helyhez nem elektromos jármű.

## `GET /parking-spots/{spotId}/reservations?from=...&to=...`

Az adott hely aktív, a kért időablakkal átfedő foglalásai. A `from` és `to` kötelező. `400`, ha `from >= to`; `404`, ha a hely nem létezik.

## `POST /reservations/{reservationId}/cancel`

Aktív foglalás lemondása. `200 OK` és a `CANCELLED` állapotú foglalás. `404`, ha nincs ilyen foglalás; `409`, ha már lemondott.

## Hibaválasz

```json docs/API.md
{
  "timestamp": "2030-06-01T08:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Parking spot is already reserved for the requested time interval",
  "path": "/reservations"
}
```
