-- Versión para H2 de sql/2026-11-04_idioma_usuario_app.sql.
-- La tabla USUARIOS_APP no forma parte del esquema compartido: cada test que la necesita crea la suya antes de llamar
-- a EsquemaH2.crear, así que la columna se añade solo si la tabla existe (y si no la tenía ya).

ALTER TABLE IF EXISTS USUARIOS_APP ADD COLUMN IF NOT EXISTS IDIOMA VARCHAR2(2) DEFAULT 'es' NOT NULL;
