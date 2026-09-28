-- ============================================================================
-- Al cancelar una sesión de entrenamiento pasa a ser obligatorio indicar
-- el motivo, para que quede registrado y se incluya en la notificación
-- a jugadores/familias. Se añade una columna para guardarlo.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no
-- usa Flyway/Liquibase). Debe ejecutarse a mano en dev y en producción
-- ANTES de desplegar el commit que cambia SesionEntrenamientoDaoImpl.
-- ============================================================================

ALTER TABLE SESIONES_ENTRENAMIENTO ADD (MOTIVO_CANCELACION VARCHAR2(500) NULL);
