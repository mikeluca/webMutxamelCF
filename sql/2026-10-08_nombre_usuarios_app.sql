-- ============================================================================
-- Nombre propio de la cuenta de la app (USUARIOS_APP.NOMBRE / APELLIDOS).
--
-- Las cuentas con una ficha vinculada (jugador, familiar, entrenador) ya tienen
-- nombre en su ficha. Las que solo tienen un rol de club sin persona
-- (coordinador, retransmisión, admin) no tenían ninguno y en el chat, en la
-- cabecera de la app y en los correos se mostraba el email. Este nombre se usa
-- solo cuando no hay ficha; los apellidos son opcionales.
--
-- Al borrar la cuenta desde la app, anonimizar() los vacía.
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no
-- usa Flyway/Liquibase). Hay que aplicarlo a mano ANTES de desplegar la
-- versión del backend que lo usa.
-- ============================================================================

ALTER TABLE USUARIOS_APP ADD (
    NOMBRE    VARCHAR2(100) NULL,
    APELLIDOS VARCHAR2(150) NULL
);
