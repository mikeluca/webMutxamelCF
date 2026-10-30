-- ============================================================================
-- Convocatoria de jugadores de otros equipos (docs/convocatoria-invitados.md, §4)
--
-- Qué añade a CONVOCATORIA_JUGADOR (todas NULL para los jugadores del propio equipo, salvo AVISO_12H_ENVIADO):
--   * EQUIPO_ORIGEN_ID: equipo habitual del invitado. NULL = jugador del propio equipo; no nulo = invitado.
--   * ESTADO_INVITACION: PENDIENTE_COORDINACION, PENDIENTE_ENTRENADOR, AUTORIZADO o RECHAZADO.
--   * FECHA_SOLICITUD: cuándo se invitó.
--   * USUARIO_INVITA_ID: quién lo invitó (el entrenador, o el coordinador que edita la convocatoria). Añadida al
--     diseño: es el destinatario de los avisos de rechazo y de desestimación, y el único que ve el motivo de la familia.
--   * USUARIO_COORDINADOR_ID y FECHA_COORDINACION: quién dio el visto bueno o rechazó, y cuándo.
--   * FECHA_PETICION_ENTRENADOR: cuándo se pidió la liberación a su entrenador (arranca el plazo de 12 horas).
--   * AVISO_12H_ENVIADO (0 / 1, por defecto 0): si ya se avisó a coordinación por el plazo vencido.
--   * FECHA_AUTORIZACION: cuándo quedó autorizado.
--   * MOTIVO_RECHAZO: motivo de coordinación, opcional.
--
-- Es una tabla que ya existe: no hace falta GRANT ni sinónimo. Solo añade columnas nulas o con valor por defecto, así
-- que el código anterior sigue funcionando. Ejecutar ANTES de desplegar, con el usuario PROPIETARIO del esquema
-- (no USER_WEB), en DEV y luego en PRO. No se ejecuta automáticamente y no se puede repetir (ORA-01430, ORA-00955):
-- si se interrumpe, continuar desde la sentencia que falló.
--
-- Pasos:
--   1) Añadir las columnas.
--   2) Restricciones: estados válidos, AVISO_12H_ENVIADO 0 / 1, y que ESTADO_INVITACION vaya siempre con
--      EQUIPO_ORIGEN_ID (un jugador del propio equipo no tiene estado).
--   3) Claves ajenas a EQUIPO y USUARIOS_APP.
--   4) Índice por equipo de origen y estado (lista de pendientes, plazo de 12 horas). El de JUGADOR_ID ya existe.
--   5) Comprobaciones.
-- ============================================================================

-- 1)
ALTER TABLE CONVOCATORIA_JUGADOR ADD (
    EQUIPO_ORIGEN_ID          NUMBER NULL,
    ESTADO_INVITACION         VARCHAR2(25) NULL,
    FECHA_SOLICITUD           TIMESTAMP NULL,
    USUARIO_INVITA_ID         NUMBER NULL,
    USUARIO_COORDINADOR_ID    NUMBER NULL,
    FECHA_COORDINACION        TIMESTAMP NULL,
    FECHA_PETICION_ENTRENADOR TIMESTAMP NULL,
    AVISO_12H_ENVIADO         NUMBER(1) DEFAULT 0 NOT NULL,
    FECHA_AUTORIZACION        TIMESTAMP NULL,
    MOTIVO_RECHAZO            VARCHAR2(500 CHAR) NULL
);

-- 2)
ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT CK_CONV_JUG_ESTADO_INV CHECK (
    ESTADO_INVITACION IN ('PENDIENTE_COORDINACION', 'PENDIENTE_ENTRENADOR', 'AUTORIZADO', 'RECHAZADO'));

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT CK_CONV_JUG_AVISO_12H CHECK (AVISO_12H_ENVIADO IN (0, 1));

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT CK_CONV_JUG_INVITADO CHECK (
    (EQUIPO_ORIGEN_ID IS NULL AND ESTADO_INVITACION IS NULL)
    OR (EQUIPO_ORIGEN_ID IS NOT NULL AND ESTADO_INVITACION IS NOT NULL));

-- 3)
ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT FK_CONV_JUG_EQUIPO_ORIGEN
    FOREIGN KEY (EQUIPO_ORIGEN_ID) REFERENCES EQUIPO(ID);

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT FK_CONV_JUG_USUARIO_INVITA
    FOREIGN KEY (USUARIO_INVITA_ID) REFERENCES USUARIOS_APP(ID);

ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT FK_CONV_JUG_USUARIO_COORD
    FOREIGN KEY (USUARIO_COORDINADOR_ID) REFERENCES USUARIOS_APP(ID);

-- 4)
CREATE INDEX IDX_CONV_JUG_ORIGEN_ESTADO ON CONVOCATORIA_JUGADOR (EQUIPO_ORIGEN_ID, ESTADO_INVITACION);

-- 5) Comprobaciones
-- Las columnas nuevas (10 filas):
SELECT COLUMN_NAME, NULLABLE, DATA_DEFAULT FROM DBA_TAB_COLUMNS
WHERE OWNER = 'SYSTEM' AND TABLE_NAME = 'CONVOCATORIA_JUGADOR'
  AND COLUMN_NAME IN ('EQUIPO_ORIGEN_ID', 'ESTADO_INVITACION', 'FECHA_SOLICITUD', 'USUARIO_INVITA_ID',
                      'USUARIO_COORDINADOR_ID', 'FECHA_COORDINACION', 'FECHA_PETICION_ENTRENADOR',
                      'AVISO_12H_ENVIADO', 'FECHA_AUTORIZACION', 'MOTIVO_RECHAZO')
ORDER BY COLUMN_ID;

-- Ninguna fila existente es invitado (debe dar INVITADOS = 0 y TOTAL = PROPIOS):
SELECT COUNT(*) AS TOTAL,
       COUNT(*) - COUNT(EQUIPO_ORIGEN_ID) AS PROPIOS,
       COUNT(EQUIPO_ORIGEN_ID) AS INVITADOS
FROM CONVOCATORIA_JUGADOR;

-- Las restricciones y los índices (las 6 restricciones nuevas y el índice IDX_CONV_JUG_ORIGEN_ESTADO):
SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM DBA_CONSTRAINTS
WHERE OWNER = 'SYSTEM' AND TABLE_NAME = 'CONVOCATORIA_JUGADOR'
  AND CONSTRAINT_NAME LIKE '%CONV_JUG%' ORDER BY CONSTRAINT_NAME;

SELECT INDEX_NAME FROM DBA_INDEXES
WHERE OWNER = 'SYSTEM' AND TABLE_NAME = 'CONVOCATORIA_JUGADOR' ORDER BY INDEX_NAME;

-- Para deshacerlo (solo si hace falta; antes hay que quitar o resolver las invitaciones que haya):
--   DROP INDEX IDX_CONV_JUG_ORIGEN_ESTADO;
--   ALTER TABLE CONVOCATORIA_JUGADOR DROP (EQUIPO_ORIGEN_ID, ESTADO_INVITACION, FECHA_SOLICITUD, USUARIO_INVITA_ID,
--       USUARIO_COORDINADOR_ID, FECHA_COORDINACION, FECHA_PETICION_ENTRENADOR, AVISO_12H_ENVIADO,
--       FECHA_AUTORIZACION, MOTIVO_RECHAZO) CASCADE CONSTRAINTS;
