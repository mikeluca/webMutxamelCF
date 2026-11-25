-- ============================================================================
-- Juego de Mutxigol: fuera Regatea (app 1.0.16, aún sin publicar)
--
-- Qué hace:
--   * Los juegos definitivos son ATRAPA, CABEZAZOS y PAREJAS.
--   * Borra las puntuaciones de prueba de REGATEA.
--   * Cambia la restricción de JUEGO_PUNTUACIONES.JUEGO a ATRAPA | CABEZAZOS | PAREJAS.
--
-- Ejecutar con el usuario propietario del esquema (SYSTEM), primero en DEV y luego en PRO.
-- ============================================================================

-- 0) Cuántas puntuaciones hay de cada juego
SELECT JUEGO, COUNT(*) AS PARTIDAS FROM JUEGO_PUNTUACIONES GROUP BY JUEGO ORDER BY JUEGO;

-- 1) Fuera las puntuaciones de Regatea
DELETE FROM JUEGO_PUNTUACIONES WHERE JUEGO = 'REGATEA';
COMMIT;

-- 2) Juegos permitidos
ALTER TABLE JUEGO_PUNTUACIONES DROP CONSTRAINT CK_JUEGO_PUNT_JUEGO;
ALTER TABLE JUEGO_PUNTUACIONES ADD CONSTRAINT CK_JUEGO_PUNT_JUEGO
    CHECK (JUEGO IN ('ATRAPA', 'CABEZAZOS', 'PAREJAS'));

-- 3) Comprobación
SELECT JUEGO, COUNT(*) AS PARTIDAS FROM JUEGO_PUNTUACIONES GROUP BY JUEGO ORDER BY JUEGO;
SELECT SEARCH_CONDITION_VC FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'CK_JUEGO_PUNT_JUEGO';
-- (Si SEARCH_CONDITION_VC no existe en tu versión de Oracle, usa SEARCH_CONDITION.)
