-- ============================================================================
-- ALTER TABLE CONVOCATORIAS: la convocatoria pasa a basarse SIEMPRE en un
-- PARTIDO ya existente, en lugar de llevar su propio rival/campo/fecha/hora
-- escritos a mano.
--
-- Flujo nuevo desde la app: el entrenador primero crea el partido (ya
-- existente, sin cambios) y después crea la convocatoria ELIGIENDO ese
-- partido; el rival/campo/fecha/hora se leen siempre en vivo mediante JOIN
-- con PARTIDOS (fuente única de verdad), nunca se duplican/snapshotean en
-- CONVOCATORIAS.
--
-- Se añade PARTIDO_ID (FK a PARTIDOS.ID) y se eliminan las columnas RIVAL,
-- CAMPO, FECHA_PARTIDO y HORA_PARTIDO, que quedan redundantes.
--
-- IMPORTANTE: no hay datos importantes en CONVOCATORIAS todavía, así que
-- este script no migra/backfillea filas existentes: si quedara alguna fila
-- de pruebas sin PARTIDO_ID no se podría poner la columna NOT NULL, por lo
-- que se vacía la tabla antes de aplicar el cambio (decisión ya tomada con
-- el cliente, igual que se hizo con la tabla PARTIDOS).
--
-- La base de datos de este proyecto es Oracle (ver
-- spring.datasource.driver-class-name=oracle.jdbc.OracleDriver), por lo que
-- se usa NUMBER y la sintaxis ALTER TABLE ... ADD/DROP de Oracle.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no usa
-- Flyway/Liquibase). Debe ejecutarse a mano en dev y en producción ANTES de
-- desplegar el commit que cambia ConvocatoriaDaoImpl/Convocatoria a
-- PARTIDO_ID.
-- ============================================================================

-- 1) No hay datos importantes en CONVOCATORIAS: se limpia la tabla (también
--    sus filas hijas en CONVOCATORIA_JUGADOR) para poder añadir PARTIDO_ID
--    como NOT NULL sin necesidad de backfill.
DELETE FROM CONVOCATORIA_JUGADOR;
DELETE FROM CONVOCATORIAS;

-- 2) Añade la columna PARTIDO_ID, obligatoria y con FK a PARTIDOS.
ALTER TABLE CONVOCATORIAS ADD (PARTIDO_ID NUMBER NOT NULL);

ALTER TABLE CONVOCATORIAS
    ADD CONSTRAINT FK_CONVOCATORIAS_PARTIDO FOREIGN KEY (PARTIDO_ID) REFERENCES PARTIDOS(ID);

-- Un partido solo puede tener, como mucho, una convocatoria.
ALTER TABLE CONVOCATORIAS
    ADD CONSTRAINT UQ_CONVOCATORIAS_PARTIDO UNIQUE (PARTIDO_ID);

CREATE INDEX IDX_CONVOCATORIAS_PARTIDO ON CONVOCATORIAS (PARTIDO_ID);

-- 3) Elimina las columnas que ahora se leen en vivo desde PARTIDOS.
ALTER TABLE CONVOCATORIAS DROP COLUMN RIVAL;
ALTER TABLE CONVOCATORIAS DROP COLUMN CAMPO;
ALTER TABLE CONVOCATORIAS DROP COLUMN FECHA_PARTIDO;
ALTER TABLE CONVOCATORIAS DROP COLUMN HORA_PARTIDO;
