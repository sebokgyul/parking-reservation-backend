# Felhasználói kézikönyv

1. Indítsd el a rendszert a repository gyökerében: `docker compose up --build -d`.
2. Kérd le a helyeket: `curl http://localhost:8080/parking-spots`.
3. Válassz aktív helyet. A `NORMAL` helyhez bármilyen, az `ELECTRIC` helyhez csak `ELECTRIC` járműtípus használható.
4. Küldj `POST /reservations` kérést az [API-leírás](API.md) mintája alapján.
5. Az időablakos foglalások megtekintéséhez használd a `GET /parking-spots/{id}/reservations?from=...&to=...` végpontot.
6. Lemondáshoz hívd a `POST /reservations/{id}/cancel` végpontot.

A PostgreSQL adat a Docker volume-ban megmarad. Teljes tiszta újraindításhoz futtasd a `docker compose down -v` parancsot.
