-- NO EJECUTAR: ya aplicado en DEV y PRO
-- ============================================================================
-- Idioma de la cuenta de la app (avisos en castellano, valenciano o inglés)
--
-- Qué añade a USUARIOS_APP:
--   * IDIOMA ('es' / 'ca' / 'en'): idioma en el que la app muestra los textos al usuario. Lo envía la app al iniciar
--     sesión, al registrar el token del móvil y al cambiarlo en Ajustes. Los avisos (bandeja, push) y los emails de
--     cuenta se generan en este idioma. Las cuentas existentes y las versiones de la app que no lo envían se quedan
--     en 'es', como hasta ahora.
--
-- Solo añade una columna con valor por defecto: el código anterior sigue funcionando. Ejecutar ANTES de desplegar,
-- con el usuario PROPIETARIO del esquema (no USER_WEB). No se ejecuta automáticamente y no se puede repetir
-- (ORA-01430): si se interrumpe, continuar desde la sentencia que falló.
--
-- Pasos:
--   1) Añadir la columna.
--   2) Restricción de valores admitidos.
--   3) Comprobación: todas las cuentas en 'es'.
-- ============================================================================

-- 1)
ALTER TABLE USUARIOS_APP ADD (
    IDIOMA  VARCHAR2(2) DEFAULT 'es' NOT NULL
);

-- 2)
ALTER TABLE USUARIOS_APP ADD CONSTRAINT CK_USUARIOS_APP_IDIOMA CHECK (IDIOMA IN ('es', 'ca', 'en'));

-- 3) Comprobación (una sola fila: IDIOMA = es con todas las cuentas)
SELECT IDIOMA, COUNT(*) AS CUENTAS FROM USUARIOS_APP GROUP BY IDIOMA;

SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE, DATA_DEFAULT
FROM USER_TAB_COLUMNS
WHERE TABLE_NAME = 'USUARIOS_APP' AND COLUMN_NAME = 'IDIOMA';

-- Para deshacerlo (solo si hace falta, y con el código anterior desplegado):
--   ALTER TABLE USUARIOS_APP DROP CONSTRAINT CK_USUARIOS_APP_IDIOMA;
--   ALTER TABLE USUARIOS_APP DROP (IDIOMA);
