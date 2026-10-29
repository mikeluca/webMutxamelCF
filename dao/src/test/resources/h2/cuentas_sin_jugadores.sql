-- Versión para H2 de sql/2026-11-01_cuentas_sin_jugadores.sql.
-- La tabla USUARIOS_APP no forma parte del esquema compartido: cada test que la necesita
-- crea la suya antes de llamar a EsquemaH2.crear, así que las columnas se añaden solo si
-- la tabla existe (y si no las tenía ya).

ALTER TABLE IF EXISTS USUARIOS_APP ADD COLUMN IF NOT EXISTS FECHA_AVISO_SIN_JUGADORES TIMESTAMP NULL;
ALTER TABLE IF EXISTS USUARIOS_APP ADD COLUMN IF NOT EXISTS DESACTIVADA_SIN_JUGADORES NUMBER(1) DEFAULT 0 NOT NULL;
