# Rendszerterv

## Architektúra

Moduláris monolit: REST controller -> service üzleti logika -> JdbcTemplate repository -> PostgreSQL. A Flyway verziózott SQL-migrációkkal hozza létre a sémát és a referenciaadatokat.

## Adatmodell

A `parking_spots` tárolja a parkolóhely kódját, típusát és aktív jelzőjét. A `reservations` egy parkolóhelyre mutató foreign key-t, kérelmezőt, járműtípust, időintervallumot, státuszt és létrehozási időt tárol. Egy helyhez több foglalás tartozhat, egy foglalás pontosan egy helyhez.

## Konkurencia és konzisztencia

Az intervallumok félig nyíltak: `[start_time, end_time)`, ezért a 10:00-11:00 és 11:00-12:00 foglalások nem ütköznek. A backend előzetesen keres átfedést a jó hibaüzenethez, de a végső garancia PostgreSQL `EXCLUDE USING gist` constraint. Ez a `btree_gist` extensionnel azonos `parking_spot_id` és átfedő `tstzrange` esetén tiltja két `ACTIVE` foglalás tárolását, így párhuzamos kéréseknél is legfeljebb egy sikeres. Constraint-sértéskor a tranzakció rollbackel és az API `409 Conflict`-ot ad.

## Teljesítmény

Az aktív foglalások időablakos lekérdezése `parking_spot_id`, kezdő és záró idő szerinti részleges indexet használ. A lekérdezéshez időablak kötelező, ezért nem olvas korlátlan történeti adatot. Az exclusion constraint GiST indexe az átfedésvizsgálatot is támogatja.
