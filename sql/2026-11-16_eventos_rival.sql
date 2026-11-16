-- ============================================================================
-- Eventos del rival en la retransmisión del Primer Equipo: tarjetas y dorsal en sus goles
--
-- Qué añade:
--   * PARTIDO_LIVE_EVENTOS.RIVAL (1 = tarjeta a un jugador del rival; 0 en todo lo demás).
--   * PARTIDO_LIVE_EVENTOS.DORSAL_RIVAL (dorsal del rival en un GOL_CONTRA o en una tarjeta del rival; opcional).
--
-- Solo añade una columna NOT NULL con valor por defecto (los eventos actuales quedan con RIVAL = 0) y otra nula:
-- el código anterior sigue funcionando.
-- Ejecutar ANTES de desplegar, con el usuario PROPIETARIO del esquema (SYSTEM), no con USER_WEB.
-- No se puede repetir entero: si se interrumpe, continuar desde la sentencia que falló.
-- ============================================================================

-- 1) Columnas nuevas
ALTER TABLE PARTIDO_LIVE_EVENTOS ADD (
    RIVAL         NUMBER(1) DEFAULT 0 NOT NULL,
    DORSAL_RIVAL  NUMBER(3) NULL
);

-- 2) Restricciones
ALTER TABLE PARTIDO_LIVE_EVENTOS ADD CONSTRAINT CK_LIVE_EVENTOS_RIVAL
    CHECK (RIVAL IN (0, 1));

ALTER TABLE PARTIDO_LIVE_EVENTOS ADD CONSTRAINT CK_LIVE_EVENTOS_RIVAL_TIPO
    CHECK (RIVAL = 0 OR (TIPO IN ('TARJETA_AMARILLA', 'TARJETA_ROJA') AND JUGADOR_ID IS NULL));

ALTER TABLE PARTIDO_LIVE_EVENTOS ADD CONSTRAINT CK_LIVE_EVENTOS_DORSAL_RIVAL
    CHECK (DORSAL_RIVAL IS NULL OR (DORSAL_RIVAL BETWEEN 0 AND 99 AND (TIPO = 'GOL_CONTRA' OR RIVAL = 1)));

-- 3) Comprobaciones
SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, NULLABLE, DATA_DEFAULT
FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'PARTIDO_LIVE_EVENTOS' AND COLUMN_NAME IN ('RIVAL', 'DORSAL_RIVAL');       -- 2 filas

SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC
FROM USER_CONSTRAINTS
WHERE TABLE_NAME = 'PARTIDO_LIVE_EVENTOS' AND CONSTRAINT_NAME LIKE 'CK_LIVE_EVENTOS_%RIVAL%'; -- 3 filas

-- Para deshacerlo (solo si hace falta, y con el código anterior desplegado):
--   ALTER TABLE PARTIDO_LIVE_EVENTOS DROP CONSTRAINT CK_LIVE_EVENTOS_DORSAL_RIVAL;
--   ALTER TABLE PARTIDO_LIVE_EVENTOS DROP CONSTRAINT CK_LIVE_EVENTOS_RIVAL_TIPO;
--   ALTER TABLE PARTIDO_LIVE_EVENTOS DROP CONSTRAINT CK_LIVE_EVENTOS_RIVAL;
--   ALTER TABLE PARTIDO_LIVE_EVENTOS DROP (RIVAL, DORSAL_RIVAL);
