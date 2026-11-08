-- ============================================================================
-- Nombre de retransmisión del jugador
--
-- Qué añade a JUGADORES:
--   * NOMBRE_RETRANSMISION (VARCHAR2(40 CHAR), opcional): cómo se llama al jugador en los avisos del partido en
--     directo y en los goleadores de la portada de la app ("Pepe", "Rubén G."). Vacío = nombre y apellidos, como hasta
--     ahora. Se rellena en el panel, en la ficha del jugador.
--
-- Solo añade una columna nula: el código anterior sigue funcionando. Ejecutar ANTES de desplegar, con el usuario
-- PROPIETARIO del esquema (no USER_WEB): el código nuevo la lee en todas las consultas de jugadores y falla sin ella.
-- No se ejecuta automáticamente y no se puede repetir (ORA-01430): si se interrumpe, continuar desde la sentencia que
-- falló.
--
-- Pasos:
--   1) Añadir la columna.
--   2) Comprobación: la columna existe, es nula por defecto y ningún jugador la trae rellena todavía.
-- ============================================================================

-- 1)
ALTER TABLE JUGADORES ADD (
    NOMBRE_RETRANSMISION  VARCHAR2(40 CHAR) NULL
);

-- 2) Comprobaciones
SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, NULLABLE, DATA_DEFAULT
FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'JUGADORES' AND COLUMN_NAME = 'NOMBRE_RETRANSMISION';   -- 1 fila: VARCHAR2, 40, Y

SELECT COUNT(*) AS CON_NOMBRE_RETRANSMISION
FROM JUGADORES
WHERE NOMBRE_RETRANSMISION IS NOT NULL;                                   -- 0

-- Para deshacerlo (solo si hace falta, y con el código anterior desplegado):
--   ALTER TABLE JUGADORES DROP (NOMBRE_RETRANSMISION);
