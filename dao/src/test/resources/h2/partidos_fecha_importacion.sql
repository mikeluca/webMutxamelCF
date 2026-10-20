-- Versión para H2 de sql/2026-10-24_partidos_fecha_importacion.sql (ya aplicado en
-- DEV y PRO). La tabla PARTIDOS no forma parte del esquema compartido: cada test que
-- la necesita crea la suya, así que aquí la columna se añade solo si la tabla existe
-- (y si no la tenía ya). Sin el UPDATE de datos existentes.

ALTER TABLE IF EXISTS PARTIDOS ADD COLUMN IF NOT EXISTS FECHA_IMPORTACION TIMESTAMP NULL;
