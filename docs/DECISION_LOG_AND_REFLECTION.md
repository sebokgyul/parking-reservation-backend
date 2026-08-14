# Döntési napló és reflexió

| # | Döntési pont | Amit választottál | Miért | Milyen alternatívát vetettél el |
|---|---|---|---|---|
| 1 | Perzisztencia | PostgreSQL | Relációs integritás, tranzakciók és natív időintervallum-támogatás (tstzrange, GiST) | Memóriabeli állapot (nem felel meg a feladatnak), dokumentumadatbázis |
| 2 | Adatelérés | Spring JdbcTemplate | Az SQL és az adatbázis-viselkedés (pl. exclusion constraint) explicit és interjún jól védhető | JPA/Hibernate (elrejti az SQL-t), nyers JDBC (túl sok boilerplate) |
| 3 | Átfedés kezelése | PostgreSQL `EXCLUDE USING gist` constraint | Párhuzamos kéréseknél is adatbázis-szintű garancia van; alkalmazásoldali ellenőrzés versenyhelyzetet okozna | Csak application-side `SELECT` ellenőrzés, explicit `SELECT ... FOR UPDATE` zárás |
| 4 | Lemondás | `CANCELLED` státusz soft delete | Auditálható marad a foglalás története, de az exclusion constraint `WHERE (status = 'ACTIVE')` miatt nem blokkol új foglalást | Fizikai `DELETE` (elveszik a_history) |
| 5 | Extra szabály | `ELECTRIC` hely csak `ELECTRIC` járműnek | Kicsi, de demonstrálható eltérő helytípus-logika a feladat opcionális részére | Teljes felhasználói/jogosultsági rendszer (túlkomplikált egy házi feladathoz) |
| 6 | Sémakezelés | Flyway verziózott migrációk | Reprodukálható, verziózott séma és seed adatok, ami induláskor garantálja a feltöltött állapotot | Nem verziózott `init.sql` (nem reprodukálható, nehéz módosítani) |

## Rövid összefoglaló

A legnagyobb kihívás az átfedő foglalások párhuzamos kezelése volt. Egy egyszerű "ellenőrző lekérdezés majd mentés" megközelítés versenyhelyzetet okozhat, ezért az alkalmazásoldali validáció mellett egy PostgreSQL exclusion constraint garantálja az invariánst. Az időintervallumokat félig nyílt formában (`[start, end)`) kezeltem, így a közvetlenül egymás után következő foglalások engedélyezettek. A tesztek írása során két rejtett hiba is felszínre került: a lemondás race condition-je és egy hiányzó hiba-kezelő, amelyeket a repository és a controller réteg módosításával javítottam.

## AI-eszköz használat

A fejlesztés során AI-asszisztenst használtam. A technikai irányítást (architektúra, adatbázis-constraintek, stack választás) én végeztem, az AI pedig az implementációs lépések kivitelezésében segített. A nyers beszélgetés-export a `docs/AI_PROMPT_HISTORY_RAW.json` fájlban található.
