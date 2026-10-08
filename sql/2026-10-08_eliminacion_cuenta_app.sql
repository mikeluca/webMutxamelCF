-- ============================================================================
-- Eliminación de cuenta desde la app (Apple Guideline 5.1.1(v)).
--
-- Cuando un usuario borra su cuenta desde la app NO se elimina la fila de
-- USUARIOS_APP: otras tablas la referencian por clave foránea (autor de
-- comunicaciones y mensajes de chat, justificaciones de falta, horarios de
-- entrenamiento...) y esos datos son del club. En su lugar se anonimiza:
-- se vacían email y contraseña, se desactiva la cuenta y se marca con esta
-- fecha. Los chats privados con una cuenta eliminada dejan de mostrarse a la
-- otra persona, pero siguen en base de datos.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no
-- usa Flyway/Liquibase). Hay que aplicarlo a mano ANTES de desplegar la
-- versión del backend que lo usa.
-- ============================================================================

ALTER TABLE USUARIOS_APP ADD (FECHA_ELIMINACION TIMESTAMP NULL);
