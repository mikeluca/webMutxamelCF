-- NO EJECUTAR: ya aplicado en DEV y PRO
-- ============================================================================
-- Cuentas de familia sin ningún jugador activo (docs/transicion-temporada.md, decisión 27)
--
-- Qué añade a USUARIOS_APP:
--   * FECHA_AVISO_SIN_JUGADORES: cuándo se avisó a la cuenta de que se desactivará. NULL = no está en "sin jugadores
--     activos". El job diario la rellena una sola vez (así el aviso no se repite) y la borra si la cuenta vuelve a
--     tener un jugador activo antes de que pasen los 3 meses.
--   * DESACTIVADA_SIN_JUGADORES (0 / 1): 1 si la cuenta la desactivó el propio job. Es lo que la distingue de una
--     cuenta desactivada a mano por un administrador, que el job NUNCA reactiva.
--
-- Qué hace el job (04:00, Europe/Madrid):
--   1. Cuenta con rol FAMILIAR o JUGADOR, sin ningún otro rol (ENTRENADOR, COORDINADOR, ADMIN_APP, RETRANSMISION),
--      activa y con todos sus jugadores de baja (o ninguno): se avisa por push y por email y se guarda
--      FECHA_AVISO_SIN_JUGADORES.
--   2. A los 3 meses del aviso se desactiva (ACTIVO = 0, DESACTIVADA_SIN_JUGADORES = 1).
--   3. Si antes tiene otra vez un jugador activo, se borra el aviso. Si ya estaba desactivada por esto, se reactiva.
--   Los jugadores de prueba (fila PRUEBA en una temporada vigente o futura) cuentan como activos aunque estén ocultos.
--
-- Solo añade columnas con valor por defecto o NULL: el código anterior sigue funcionando. Ejecutar ANTES de
-- desplegar, con el usuario PROPIETARIO del esquema (no USER_WEB). No se ejecuta automáticamente y no se puede
-- repetir (ORA-01430): si se interrumpe, continuar desde la sentencia que falló.
--
-- Pasos:
--   1) Añadir las dos columnas.
--   2) Restricción 0 / 1 de DESACTIVADA_SIN_JUGADORES.
--   3) Índice para que el job localice las cuentas con aviso.
--   4) Comprobación: todas las cuentas con 0 desactivadas y 0 avisadas.
-- ============================================================================

-- 1)
ALTER TABLE USUARIOS_APP ADD (
    FECHA_AVISO_SIN_JUGADORES  TIMESTAMP NULL,
    DESACTIVADA_SIN_JUGADORES  NUMBER(1) DEFAULT 0 NOT NULL
);

-- 2)
ALTER TABLE USUARIOS_APP ADD CONSTRAINT CK_USUARIOS_APP_DESACT_SINJ CHECK (DESACTIVADA_SIN_JUGADORES IN (0, 1));

-- 3)
CREATE INDEX IDX_USUARIOS_APP_SIN_JUGADORES ON USUARIOS_APP (FECHA_AVISO_SIN_JUGADORES);

-- 4) Comprobación
SELECT COUNT(*) AS CUENTAS,
       COUNT(FECHA_AVISO_SIN_JUGADORES) AS AVISADAS,
       SUM(DESACTIVADA_SIN_JUGADORES) AS DESACTIVADAS_POR_EL_JOB
FROM USUARIOS_APP;

-- Para deshacerlo (solo si hace falta):
--   DROP INDEX IDX_USUARIOS_APP_SIN_JUGADORES;
--   ALTER TABLE USUARIOS_APP DROP CONSTRAINT CK_USUARIOS_APP_DESACT_SINJ;
--   ALTER TABLE USUARIOS_APP DROP (FECHA_AVISO_SIN_JUGADORES, DESACTIVADA_SIN_JUGADORES);
