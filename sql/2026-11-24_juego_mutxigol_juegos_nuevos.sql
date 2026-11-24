-- ============================================================================
-- Juego de Mutxigol: cambio de juegos (app 1.0.16, aún sin publicar)
--
-- Qué hace:
--   * Penaltis y Toques desaparecen. Entran Regatea (carrera infinita), Cabezazos (estilo Flappy Bird)
--     y Parejas (memoria de escudos). Atrapa balones se queda.
--   * Borra las puntuaciones de prueba de PENALTIS y TOQUES (la 1.0.16 no está publicada: solo hay pruebas).
--   * Cambia la restricción de JUEGO_PUNTUACIONES.JUEGO a ATRAPA | REGATEA | CABEZAZOS | PAREJAS.
--   * En PAREJAS se guarda PUNTOS = número de parejas (8) y la marca es DURACION_MS (menos es mejor):
--     no hace falta ninguna columna nueva.
--
-- Ejecutar con el usuario propietario del esquema (SYSTEM), primero en DEV y luego en PRO.
-- ============================================================================

-- 0) Cuántas puntuaciones hay de cada juego (las de PENALTIS y TOQUES se van a borrar)
SELECT JUEGO, COUNT(*) AS PARTIDAS FROM JUEGO_PUNTUACIONES GROUP BY JUEGO ORDER BY JUEGO;

-- 1) Fuera las puntuaciones de los juegos que desaparecen
DELETE FROM JUEGO_PUNTUACIONES WHERE JUEGO IN ('PENALTIS', 'TOQUES');
COMMIT;

-- 2) Nuevos juegos permitidos
ALTER TABLE JUEGO_PUNTUACIONES DROP CONSTRAINT CK_JUEGO_PUNT_JUEGO;
ALTER TABLE JUEGO_PUNTUACIONES ADD CONSTRAINT CK_JUEGO_PUNT_JUEGO
    CHECK (JUEGO IN ('ATRAPA', 'REGATEA', 'CABEZAZOS', 'PAREJAS'));

-- 3) Comprobación: solo quedan ATRAPA (o nada) y la restricción nueva
SELECT JUEGO, COUNT(*) AS PARTIDAS FROM JUEGO_PUNTUACIONES GROUP BY JUEGO ORDER BY JUEGO;
SELECT SEARCH_CONDITION_VC FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'CK_JUEGO_PUNT_JUEGO';
-- (Si SEARCH_CONDITION_VC no existe en tu versión de Oracle, usa SEARCH_CONDITION.)
