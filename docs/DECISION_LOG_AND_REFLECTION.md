# Döntési napló és reflexió

| Döntési pont | Választás | Miért | Elvetett alternatíva |
|---|---|---|---|
| Perzisztencia | PostgreSQL | Relációs integritás, tranzakciók és natív időintervallum-támogatás | Memóriabeli állapot, dokumentumadatbázis |
| Adatelérés | Spring JdbcTemplate | Az SQL explicit és jól védhető interjún | JPA/Hibernate, nyers JDBC |
| Átfedés kezelése | PostgreSQL exclusion constraint | Párhuzamos kéréseknél is adatbázis-szintű garancia | Csak application-side ellenőrzés, explicit lock |
| Lemondás | `CANCELLED` státusz | Auditálható marad a foglalás, de nem blokkol új igényt | Fizikai `DELETE` |
| Extra szabály | ELECTRIC hely csak ELECTRIC járműnek | Kicsi, demonstrálható eltérő helytípus-logika | Teljes felhasználói jogosultsági rendszer |
| Sémakezelés | Flyway | Reprodukálható, verziózott séma és seed adatok | Nem verziózott init.sql |

## Rövid reflexió

A legnagyobb kihívás az átfedő foglalások párhuzamos kezelésének megértése volt. Az egyszerű lekérdezés, majd mentés versenyhelyzetet okozhat, ezért az alkalmazásoldali validáció mellett PostgreSQL exclusion constraint garantálja az invariánst. Az időintervallumokat félig nyílt formában kezeltem, így a közvetlenül egymás után következő foglalások engedélyezettek. A rendszer JDBC-t használ, hogy a fontos SQL és adatbázis-viselkedés explicit maradjon. AI-asszisztenst használtam a követelmények értelmezésére, rendszertervezési döntések megvitatására, tanulásra és implementációs támogatásra; a tényleges nyers prompt exportot beadás előtt külön kell csatolni.
