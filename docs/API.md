# API-leírás

Az üzleti API JSON-t használ. A `POST /reservations` kéréshez `Content-Type: application/json` szükséges. Az időpontok ISO-8601 formátumú, offsetet tartalmazó értékek, például `2030-06-10T10:00:00+02:00`; másodperc és nanoszekundum nem adható meg.

## Adattípusok

### Parkolóhely

```json
{
  "id": 1,
  "code": "A-01",
  "spotType": "NORMAL",
  "active": true
}
```

- `spotType`: `NORMAL` vagy `ELECTRIC`.
- `active`: `false` helyre új foglalás nem hozható létre.

### Foglalás

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

- `vehicleType`: `STANDARD` vagy `ELECTRIC`.
- `status`: `ACTIVE` vagy `CANCELLED`.

## `GET /parking-spots`

Az összes tárolt parkolóhelyet adja vissza kód szerint rendezve, az inaktívakat is beleértve.

- Siker: `200 OK`, a válasz Parkolóhelyek JSON tömbje.

## `POST /reservations`

Új foglalást hoz létre. Minta kérés:

```json
{
  "parkingSpotId": 1,
  "requesterName": "Anna Kovacs",
  "vehicleType": "STANDARD",
  "startTime": "2030-06-10T10:00:00+02:00",
  "endTime": "2030-06-10T11:00:00+02:00"
}
```

Minden mező kötelező. A `requesterName` a tárolás előtt trimelődik, nem lehet üres, és legfeljebb 100 karakter lehet. `startTime`-nak `endTime` előtt kell lennie, nem lehet múltbeli, és mindkét értéknek perc-pontosságúnak kell lennie.

Szabályok:

- csak aktív hely foglalható;
- `ELECTRIC` helyhez csak `ELECTRIC` jármű foglalhat;
- az aktív foglalások félig nyílt intervallumai `[startTime, endTime)` nem fedhetik át egymást ugyanazon a helyen.

- Siker: `201 Created`, a létrehozott Foglalás JSON objektuma.
- Hiba: `400 Bad Request` hibás vagy hiányzó értéknél; `404 Not Found`, ha a hely nem létezik; `409 Conflict` inaktív hely, nem megfelelő járműtípus vagy átfedés esetén.

## `GET /parking-spots/{spotId}/reservations?from=...&to=...`

Az adott hely azon aktív foglalásait adja vissza kezdési idő szerint rendezve, amelyek átfednek a kért intervallummal: `startTime < to` és `endTime > from`.

- `spotId`: a parkolóhely azonosítója.
- `from` és `to`: kötelező, offsetes ISO-8601 időpontok, perc-pontossággal; `from < to` kell teljesüljön.
- Siker: `200 OK`, Foglalások JSON tömbje. Üres tömb érvényes válasz.
- Hiba: `400 Bad Request` hiányzó, hibás vagy nem növekvő időablaknál; `404 Not Found`, ha a hely nem létezik.

## `POST /reservations/{reservationId}/cancel`

Aktív foglalást mond le. Nincs kérés törzs.

- Siker: `200 OK`, a `CANCELLED` állapotú Foglalás JSON objektuma.
- Hiba: `404 Not Found`, ha nincs ilyen foglalás; `409 Conflict`, ha a foglalás már le van mondva, vagy egy párhuzamos lemondás már végrehajtotta a módosítást.

## Üzemeltetési végpont

`GET /actuator/health` az alkalmazás health státuszát adja vissza, például `{"status":"UP"}`. Ez nem üzleti végpont.

## Hibaválasz

Minden kezelt hiba egységes formátumú:

```json
{
  "timestamp": "2030-06-01T08:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Parking spot is already reserved for the requested time interval",
  "path": "/reservations"
}
```

A nem támogatott HTTP-metódus `405 Method Not Allowed`, a `POST /reservations` nem JSON médiatípusa `415 Unsupported Media Type` választ ad.
