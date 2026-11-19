-- Version para H2 de sql/2026-11-19_imagenes_redes_fondos.sql: la misma tabla y restricciones CHECK, sin permisos ni sinonimos.
CREATE TABLE IMAGENES_REDES_FONDOS (
    CARTEL               VARCHAR2(20) PRIMARY KEY,
    IMAGEN               BLOB NOT NULL,
    TIPO_CONTENIDO       VARCHAR2(30) NOT NULL,
    ANCHO                NUMBER(5) NOT NULL,
    ALTO                 NUMBER(5) NOT NULL,
    FECHA_ACTUALIZACION  TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    USUARIO_ACTUALIZO    VARCHAR2(100 CHAR) NULL,
    CONSTRAINT CK_IRF_CARTEL CHECK (CARTEL IN ('PRIMER_EQUIPO', 'FILIAL')),
    CONSTRAINT CK_IRF_TIPO CHECK (TIPO_CONTENIDO IN ('image/jpeg', 'image/png'))
);
