-- ============================================================================
-- Une las dos funcionalidades de entrenamientos que hasta ahora vivían por
-- separado: el calendario de sesiones (SESIONES_ENTRENAMIENTO) y el registro
-- de asistencia (ENTRENAMIENTOS / ENTRENAMIENTO_ASISTENCIA). A partir de
-- ahora, cada vez que se crea una sesión (generada desde un horario o
-- suelta) se crea automáticamente su ENTRENAMIENTO, con todos los jugadores
-- del equipo a PRESENTE; si la sesión se cancela, el ENTRENAMIENTO generado
-- y sus asistencias se eliminan.
--
-- SESION_ENTRENAMIENTO_ID queda NULL en los ENTRENAMIENTOS creados a mano
-- antes de esta funcionalidad: no hace falta rellenarlo, no hay datos
-- importantes que preservar de otra forma. UNIQUE(SESION_ENTRENAMIENTO_ID)
-- garantiza como mucho un ENTRENAMIENTO por sesión (Oracle permite múltiples
-- NULLs en una columna UNIQUE, así que el histórico sin vincular no choca
-- entre sí ni con las sesiones nuevas).
--
-- IMPORTANTE: este script NO se ejecuta automáticamente (el proyecto no usa
-- Flyway/Liquibase). Debe ejecutarse a mano en dev y en producción ANTES de
-- desplegar el commit que cambia EntrenamientoDaoImpl / EntrenamientoServic
-- eImpl / SesionEntrenamientoServiceImpl.
-- ============================================================================

ALTER TABLE ENTRENAMIENTOS ADD (SESION_ENTRENAMIENTO_ID NUMBER NULL);

ALTER TABLE ENTRENAMIENTOS ADD CONSTRAINT FK_ENTRENAMIENTOS_SESION
    FOREIGN KEY (SESION_ENTRENAMIENTO_ID) REFERENCES SESIONES_ENTRENAMIENTO(ID);

ALTER TABLE ENTRENAMIENTOS ADD CONSTRAINT UQ_ENTRENAMIENTOS_SESION
    UNIQUE (SESION_ENTRENAMIENTO_ID);
