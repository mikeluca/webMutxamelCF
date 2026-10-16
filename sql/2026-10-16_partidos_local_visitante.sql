-- ============================================================================
-- PARTIDOS.ES_LOCAL: 1 si el Mutxamel juega como local, 0 si es visitante.
--
-- La app muestra al equipo local a la izquierda y al visitante a la derecha.
-- Los partidos ya existentes quedan como locales (comportamiento anterior);
-- el importador FFCV los corrige en la siguiente importación.
--
-- IMPORTANTE: se ejecuta a mano en dev y producción ANTES de desplegar.
-- ============================================================================

ALTER TABLE PARTIDOS ADD (ES_LOCAL NUMBER(1) DEFAULT 1 NOT NULL);
