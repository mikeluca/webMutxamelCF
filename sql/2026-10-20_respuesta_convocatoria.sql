-- ============================================================================
-- Respuesta de jugadores y familias a la convocatoria ("Va" / "No puede").
--
-- CONVOCATORIA_JUGADOR guarda ahora la respuesta de cada jugador convocado:
--   RESPUESTA                 'VA' | 'NO_PUEDE'. NULL = sin responder.
--   MOTIVO                    obligatorio (3-500 caracteres) con NO_PUEDE; se
--                             guarda a NULL con VA.
--   FECHA_RESPUESTA           cuándo respondió (hora de Madrid).
--   USUARIO_APP_RESPUESTA_ID  quién respondió (el propio jugador o un familiar).
--
-- "No puede" con motivo es también la justificación de la ausencia al partido:
-- no hay otra tabla. Se puede cambiar la respuesta hasta la HORA_CONVOCATORIA
-- del día del partido (el plazo lo calcula el servicio, no la base de datos).
--
-- Son columnas nuevas de una tabla que ya existe y que USER_WEB ya puede usar
-- (GRANT y sinónimo ya creados): no hace falta ningún GRANT ni sinónimo nuevo.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no usa
-- Flyway/Liquibase). Debe ejecutarse a mano en dev y en producción ANTES de
-- desplegar el commit que añade las respuestas a la convocatoria.
-- ============================================================================

ALTER TABLE CONVOCATORIA_JUGADOR ADD (
    RESPUESTA                 VARCHAR2(10) NULL,
    MOTIVO                    VARCHAR2(500) NULL,
    FECHA_RESPUESTA           TIMESTAMP NULL,
    USUARIO_APP_RESPUESTA_ID  NUMBER NULL
);

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT CK_CONV_JUG_RESPUESTA
    CHECK (RESPUESTA IN ('VA', 'NO_PUEDE'));

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT FK_CONV_JUG_USUARIO_RESP
    FOREIGN KEY (USUARIO_APP_RESPUESTA_ID) REFERENCES USUARIOS_APP(ID);

-- Para "mis convocatorias": buscar las convocatorias de un jugador.
CREATE INDEX IDX_CONV_JUG_JUGADOR ON CONVOCATORIA_JUGADOR (JUGADOR_ID);

-- Comprobación: debe listar las 4 columnas nuevas (todas NULL en las filas
-- existentes, es decir, "sin responder").
SELECT COLUMN_NAME, DATA_TYPE, NULLABLE
FROM ALL_TAB_COLUMNS
WHERE TABLE_NAME = 'CONVOCATORIA_JUGADOR'
  AND COLUMN_NAME IN ('RESPUESTA', 'MOTIVO', 'FECHA_RESPUESTA', 'USUARIO_APP_RESPUESTA_ID')
ORDER BY COLUMN_NAME;
