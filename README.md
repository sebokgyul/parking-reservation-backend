# Parkolóhely-foglalás backend

Java 21 / Spring Boot / JdbcTemplate / PostgreSQL megoldás parkolóhely-foglalások kezelésére.

## Előfeltételek

A teljes rendszer indításához futó Docker Engine és Docker Compose v2 (`docker compose`) szükséges. Az első indításkor internetkapcsolat kell a Docker image-ek és a Maven-függőségek letöltéséhez. Lokális JDK vagy Maven telepítése az alkalmazás Dockeres indításához nem szükséges, mert a Docker build Java 21-et használ.

A Maven tesztcsomag futtatásához Java 21 JDK és futó Docker Engine kell: az integrációs tesztek Testcontainers-szel PostgreSQL-konténert indítanak.

```bash
java --version   # Java 21
docker info      # a Docker daemon elérhető
./mvnw test
```

## Indítás

```bash
docker compose up --build -d
```

Az API a `http://localhost:8080` címen érhető el. Indulási logok követése:

```bash
docker compose logs -f backend
```

Leállítás:

```bash
docker compose down
```

Az adatbázis teljes törlése szükség esetén:

```bash
docker compose down -v
```

## Dokumentáció

- [API-leírás](docs/API.md)
- [Rendszerterv](docs/SYSTEM_DESIGN.md)
- [Felhasználói kézikönyv](docs/USER_GUIDE.md)
- [Döntési napló és reflexió](docs/DECISION_LOG_AND_REFLECTION.md)
- [AI prompt history](docs/AI_PROMPT_HISTORY_RAW.json)
