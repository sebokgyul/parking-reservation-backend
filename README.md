# Parkolóhely-foglalás backend

Java 21 / Spring Boot / JdbcTemplate / PostgreSQL megoldás parkolóhely-foglalások kezelésére.

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
