-- NO EJECUTAR: ya aplicado en DEV y PRO
-- ============================================================================
-- JUGADOR_TEMPORADA.FECHA_PREGUNTA_CONTINUIDAD: cuándo se preguntó a las familias si el jugador sigue
--
-- La usa "Pedir confirmación de continuidad" (job del 1 de mayo y botón del asistente) para ser idempotente: solo se
-- pregunta, y se envía el push, a los jugadores cuya fila todavía no tiene esta fecha. El recordatorio semanal solo
-- recuerda a quien ya ha sido preguntado y sigue en CONTINUIDAD = 'PENDIENTE'.
--
-- Solo añade una columna NULL: el código anterior sigue funcionando. Ejecutar ANTES de desplegar, con el usuario
-- PROPIETARIO del esquema (no USER_WEB). No se ejecuta automáticamente y no se puede repetir (ORA-01430).
-- ============================================================================

ALTER TABLE JUGADOR_TEMPORADA ADD (FECHA_PREGUNTA_CONTINUIDAD TIMESTAMP NULL);

-- Comprobación: debe devolver la columna, todas las filas con NULL.
SELECT COUNT(*) AS FILAS, COUNT(FECHA_PREGUNTA_CONTINUIDAD) AS PREGUNTADAS FROM JUGADOR_TEMPORADA;
