-- NO EJECUTAR todavía: pendiente de aplicar en DEV y PRO
-- ============================================================================
-- Nuevo rol de la app: RRSS (Redes sociales)
--
-- NOTA: este fichero lo ha creado Claude porque el script original no estaba en la carpeta del proyecto. Si tienes el
-- tuyo, sustitúyelo por él.
--
-- Qué añade a ROLES_APP:
--   * Una fila CODIGO = 'RRSS', NOMBRE = 'Redes sociales'. Quien lo tiene gestiona las imágenes para redes (previa,
--     resultados y carteles del Primer Equipo y del Filial) y recibe sus recordatorios. RETRANSMISION queda solo para
--     el partido en directo; ADMIN_APP accede a todo.
--
-- Ejecutar ANTES de desplegar, con el usuario PROPIETARIO del esquema. Si el rol no existe, el panel y la app fallan
-- al invitar o editar una cuenta con RRSS ("El rol RRSS no está configurado en ROLES_APP").
-- Se puede repetir: no inserta si ya existe.
--
-- Pasos:
--   1) Insertar el rol.
--   2) Comprobación: 1 fila.
-- ============================================================================

-- 1)
INSERT INTO ROLES_APP (ID, CODIGO, NOMBRE)
SELECT NVL(MAX(ID), 0) + 1, 'RRSS', 'Redes sociales'
FROM ROLES_APP
WHERE NOT EXISTS (SELECT 1 FROM ROLES_APP WHERE CODIGO = 'RRSS');
COMMIT;

-- 2)
SELECT ID, CODIGO, NOMBRE FROM ROLES_APP WHERE CODIGO = 'RRSS';   -- 1 fila

-- Marcha atrás (solo si ninguna cuenta lo tiene):
--   DELETE FROM ROLES_APP WHERE CODIGO = 'RRSS';
