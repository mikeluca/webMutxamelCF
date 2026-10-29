-- NO EJECUTAR: ya aplicado (con la corrección manual del 30/10/2026)
-- ============================================================================
-- JUGADOR_TEMPORADA: se elimina la columna BAJA (la baja es ESTADO = 'BAJA')
--
-- Desde 2026-10-29_modelo_transicion_temporada.sql (parte B) BAJA estaba sincronizada con ESTADO por el CHECK
-- CK_JT_BAJA_ESTADO. El código nuevo solo lee y escribe ESTADO, así que la columna y su CHECK sobran.
--
-- BAJA también estaba en el CHECK multicolumna CK_JUG_TEMP_FLAGS (BAJA IN (0,1) AND PENDIENTE_REVISION IN (0,1)),
-- creado en 2026-10-29_plantillas_temporada.sql: Oracle no deja borrar una columna que forma parte de un CHECK
-- multicolumna (ORA-12991). Se sustituye por CK_JT_PENDIENTE_REVISION, que conserva lo que no era de BAJA.
--
-- REQUISITOS: 2026-10-29_modelo_transicion_temporada.sql (partes A y B) ya aplicado.
--
-- CUÁNDO: JUSTO ANTES de desplegar el código nuevo, con el usuario PROPIETARIO del esquema (no USER_WEB). El
-- código anterior sigue leyendo y escribiendo BAJA, así que en cuanto se ejecuta este script (y hasta que termina
-- el despliegue) la pantalla de plantillas, las estadísticas de equipo y el asistente "Nueva temporada" fallan con
-- ORA-00904. Dejar esa ventana lo más corta posible. No hay que ejecutar nada después del despliegue.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no usa Flyway/Liquibase). No se puede repetir:
-- la segunda vez falla porque ya no existe la columna.
-- ============================================================================

-- Comprobación previa: debe dar 0 (BAJA y ESTADO coherentes, como obliga CK_JT_BAJA_ESTADO).
SELECT COUNT(*) AS INCOHERENTES
FROM JUGADOR_TEMPORADA
WHERE (BAJA = 1 AND ESTADO <> 'BAJA') OR (BAJA = 0 AND ESTADO = 'BAJA');

-- Estado actual, para comparar al final (las bajas no pueden cambiar).
SELECT ESTADO, COUNT(*) AS FILAS FROM JUGADOR_TEMPORADA GROUP BY ESTADO ORDER BY ESTADO;

-- 1) Se quita el CHECK que ligaba las dos columnas.
ALTER TABLE JUGADOR_TEMPORADA DROP CONSTRAINT CK_JT_BAJA_ESTADO;

-- 2) Por si alguna escritura del código anterior dejó BAJA = 1 con otro ESTADO, la baja manda.
UPDATE JUGADOR_TEMPORADA SET ESTADO = 'BAJA' WHERE BAJA = 1 AND ESTADO <> 'BAJA';

-- 3) El CHECK multicolumna que incluye BAJA se sustituye por uno solo de PENDIENTE_REVISION (si no, el DROP COLUMN
-- falla con ORA-12991).
ALTER TABLE JUGADOR_TEMPORADA DROP CONSTRAINT CK_JUG_TEMP_FLAGS;
ALTER TABLE JUGADOR_TEMPORADA ADD CONSTRAINT CK_JT_PENDIENTE_REVISION CHECK (PENDIENTE_REVISION IN (0, 1));

-- 4) Se quita la columna.
ALTER TABLE JUGADOR_TEMPORADA DROP COLUMN BAJA;

COMMIT;

-- Comprobación 1: la columna ya no existe (0 filas).
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'JUGADOR_TEMPORADA' AND COLUMN_NAME = 'BAJA';

-- Comprobación 2: los CHECK que tenían BAJA ya no existen (0 filas) y están los de ESTADO, CONTINUIDAD,
-- EQUIPO_DEFINITIVO y PENDIENTE_REVISION (4 filas).
SELECT CONSTRAINT_NAME FROM USER_CONSTRAINTS
WHERE TABLE_NAME = 'JUGADOR_TEMPORADA' AND CONSTRAINT_NAME IN ('CK_JT_BAJA_ESTADO', 'CK_JUG_TEMP_FLAGS');

SELECT CONSTRAINT_NAME FROM USER_CONSTRAINTS
WHERE TABLE_NAME = 'JUGADOR_TEMPORADA' AND CONSTRAINT_NAME IN
      ('CK_JT_ESTADO', 'CK_JT_CONTINUIDAD', 'CK_JT_EQUIPO_DEFINITIVO', 'CK_JT_PENDIENTE_REVISION')
ORDER BY CONSTRAINT_NAME;

-- Comprobación 3: mismos recuentos por ESTADO que antes.
SELECT ESTADO, COUNT(*) AS FILAS FROM JUGADOR_TEMPORADA GROUP BY ESTADO ORDER BY ESTADO;
