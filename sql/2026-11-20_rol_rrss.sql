-- ============================================================================
-- Nuevo rol de la app: RRSS (redes sociales)
--
-- Qué hace:
--   * Añade el rol RRSS a ROLES_APP. Las cuentas con RRSS solo ven "Directo y RRSS › Imágenes para redes"
--     (previa, resultados y carteles). RETRANSMISION pasa a ver solo "Partido en directo".
--   * (Paso 3, opcional) Da RRSS a las cuentas que hoy tienen RETRANSMISION, para que nadie pierda
--     las imágenes al desplegar. Después puedes quitar a cada uno el rol que no necesite desde el panel.
--
-- EJECUTADO en DEV y PRO el 20/11/2026 con el usuario propietario del esquema (SYSTEM).
-- Se puede repetir sin problemas (no duplica nada).
-- ============================================================================

-- 0) Mira cómo está la tabla (columnas ID, CODIGO, NOMBRE y los roles actuales)
SELECT ID, CODIGO, NOMBRE FROM ROLES_APP ORDER BY ID;

-- 1) Nuevo rol
INSERT INTO ROLES_APP (ID, CODIGO, NOMBRE)
SELECT NVL(MAX(ID), 0) + 1, 'RRSS', 'Redes sociales'
FROM ROLES_APP
WHERE NOT EXISTS (SELECT 1 FROM ROLES_APP WHERE CODIGO = 'RRSS');

-- Si el paso 1 da ORA-32795 (ID generado automáticamente "ALWAYS"), usa este en su lugar:
--   INSERT INTO ROLES_APP (CODIGO, NOMBRE)
--   SELECT 'RRSS', 'Redes sociales' FROM DUAL
--   WHERE NOT EXISTS (SELECT 1 FROM ROLES_APP WHERE CODIGO = 'RRSS');

COMMIT;

-- 2) Comprobación: 1 fila
SELECT ID, CODIGO, NOMBRE FROM ROLES_APP WHERE CODIGO = 'RRSS';

-- 3) OPCIONAL: dar RRSS a quien hoy tiene RETRANSMISION (para que siga viendo las imágenes)
--    Antes, mira quiénes son:
SELECT U.ID, U.NOMBRE, U.APELLIDOS, U.EMAIL
FROM USUARIOS_APP U
JOIN USUARIOS_APP_ROLES UR ON UR.USUARIO_APP_ID = U.ID
JOIN ROLES_APP R ON R.ID = UR.ROL_ID
WHERE R.CODIGO = 'RETRANSMISION' AND U.FECHA_ELIMINACION IS NULL;

--    Si quieres dárselo a todos ellos:
-- INSERT INTO USUARIOS_APP_ROLES (USUARIO_APP_ID, ROL_ID)
-- SELECT UR.USUARIO_APP_ID, (SELECT ID FROM ROLES_APP WHERE CODIGO = 'RRSS')
-- FROM USUARIOS_APP_ROLES UR
-- JOIN ROLES_APP R ON R.ID = UR.ROL_ID
-- WHERE R.CODIGO = 'RETRANSMISION'
--   AND NOT EXISTS (SELECT 1 FROM USUARIOS_APP_ROLES X
--                   WHERE X.USUARIO_APP_ID = UR.USUARIO_APP_ID
--                     AND X.ROL_ID = (SELECT ID FROM ROLES_APP WHERE CODIGO = 'RRSS'));
-- COMMIT;

-- Para deshacerlo (solo con el backend anterior desplegado):
--   DELETE FROM USUARIOS_APP_ROLES WHERE ROL_ID = (SELECT ID FROM ROLES_APP WHERE CODIGO = 'RRSS');
--   DELETE FROM ROLES_APP WHERE CODIGO = 'RRSS';
--   COMMIT;
