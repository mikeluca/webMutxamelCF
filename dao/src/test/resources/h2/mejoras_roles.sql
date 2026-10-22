-- Versión para H2 de sql/2026-10-27_mejoras_roles.sql (ya aplicado en DEV y PRO).
-- La tabla USUARIOS_APP_DISPOSITIVOS no forma parte del esquema compartido: la columna se
-- añade solo si la tabla existe (y si no la tenía ya).

ALTER TABLE IF EXISTS USUARIOS_APP_DISPOSITIVOS ADD COLUMN IF NOT EXISTS VERSION_APP VARCHAR(20) NULL;

ALTER TABLE PARTIDO_LIVE_EVENTOS ADD COLUMN IF NOT EXISTS MINUTO NUMBER(3) NULL;
