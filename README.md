# Parkolóhely-foglalás backend

Java 21 / Spring Boot / JdbcTemplate / PostgreSQL megoldás parkolóhely-foglalások kezelésére.

## Indítás

```bash README.md
docker compose up --build
```

Az API a `http://localhost:8080` címen érhető el. Leállítás:

```bash README.md
docker compose down
```

Az adatbázis teljes törlése is szükséges esetén:

```bash README.md
docker compose down -v
```

Részletes dokumentáció: [API](docs/API.md), [rendszerterv](docs/SYSTEM_DESIGN.md), [használati útmutató](docs/USER_GUIDE.md), [döntési napló](docs/DECISION_LOG_AND_REFLECTION.md).
