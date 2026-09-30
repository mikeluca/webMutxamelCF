package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import java.time.LocalDate;
import java.util.List;

public interface EntrenamientoService {

        EntrenamientoResponse crear(
                        Long usuarioAppId,
                        EntrenamientoGuardarRequest request);

        EntrenamientoResponse actualizar(
                        Long usuarioAppId,
                        Long entrenamientoId,
                        EntrenamientoGuardarRequest request);

        EntrenamientoResponse obtenerPorId(
                        Long usuarioAppId,
                        Long entrenamientoId);

        List<EntrenamientoResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId);

        /**
         * Crea automáticamente el ENTRENAMIENTO (registro de asistencia)
         * ligado a una sesión del calendario recién creada, con todos los
         * jugadores del equipo a PRESENTE. Pensado para ser llamado desde
         * SesionEntrenamientoServiceImpl justo después de crear la sesión
         * (tanto generada desde un horario como suelta), nunca desde el
         * controlador.
         *
         * Idempotente: si esa sesión ya tiene un ENTRENAMIENTO vinculado no
         * hace nada, para que se pueda invocar tanto desde la generación
         * "al vuelo" como desde el job mensual sin duplicar. Si no se puede
         * determinar un autor (usuarioAutorId nulo), no crea nada y registra
         * un aviso, ya que USUARIO_ENTRENADOR_ID es obligatorio en
         * ENTRENAMIENTOS.
         *
         * No genera las comunicaciones de FALTA/MAL_COMPORTAMIENTO: todos
         * los jugadores quedan a PRESENTE, así que no aplica.
         */
        void crearAutomaticoParaSesion(
                        Long sesionEntrenamientoId,
                        Long equipoId,
                        LocalDate fecha,
                        Long usuarioAutorId);

        /**
         * Elimina el ENTRENAMIENTO vinculado a una sesión del calendario
         * (y sus asistencias), si existe. No-op si la sesión no tiene
         * ningún ENTRENAMIENTO vinculado (p.ej. sesiones anteriores a esta
         * funcionalidad). Pensado para llamarse al cancelar una sesión,
         * individualmente o en bloque.
         */
        void eliminarPorSesionEntrenamientoId(
                        Long sesionEntrenamientoId);

        /**
         * Sincroniza, de forma best-effort, el estado de un jugador en el
         * ENTRENAMIENTO vinculado a una sesión del calendario tras una
         * justificación de falta en adelanto (SesionEntrenamientoServiceImpl
         * .justificar). Si esa sesión no tiene ningún ENTRENAMIENTO
         * vinculado, o el jugador no está entre sus asistencias, no hace
         * nada y registra un aviso, sin lanzar ninguna excepción: la
         * justificación (JustificacionFaltaEntrenamiento) es la fuente de
         * verdad de todos modos, esto es solo una comodidad para que el
         * entrenador la vea reflejada si abre el registro de asistencia.
         */
        void sincronizarEstadoPorJustificacion(
                        Long sesionEntrenamientoId,
                        Long jugadorId,
                        String estado);

        /**
         * Estado de asistencia (PRESENTE/FALTA/FALTA_JUSTIFICADA/
         * TARDANZA...) de un jugador en el ENTRENAMIENTO vinculado a una
         * sesión del calendario, o {@code null} si esa sesión no tiene
         * ningún ENTRENAMIENTO vinculado o el jugador no está entre sus
         * asistencias.
         */
        String obtenerEstadoAsistencia(
                        Long sesionEntrenamientoId,
                        Long jugadorId);
}