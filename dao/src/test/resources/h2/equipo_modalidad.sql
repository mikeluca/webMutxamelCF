-- Versión para H2 de sql/2026-10-26_equipo_modalidad.sql (ya aplicado en DEV y PRO).
-- La tabla EQUIPO no forma parte del esquema compartido: cada test que la necesita crea la
-- suya, así que la columna se añade solo si la tabla existe (y si no la tenía ya).

ALTER TABLE IF EXISTS EQUIPO ADD COLUMN IF NOT EXISTS MODALIDAD VARCHAR(3) NULL;
ALTER TABLE IF EXISTS EQUIPO ADD CONSTRAINT IF NOT EXISTS CK_EQUIPO_MODALIDAD CHECK (MODALIDAD IN ('F11', 'F8', 'F5'));
