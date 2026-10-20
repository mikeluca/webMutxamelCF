package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;

/**
 * Ausencia justificada a partidos. La justifica el propio jugador o un
 * familiar (vínculo con el jugador) hasta la hora de convocatoria del día del
 * partido; la consulta el cuerpo técnico del equipo.
 */
public interface JustificacionFaltaPartidoService {

    /** Crea o actualiza la justificación (403 sin vínculo; 400 si no procede o fuera de plazo). */
    JustificacionFaltaPartidoResponse justificar(Long usuarioAppId, Long partidoId, JustificacionFaltaRequest request);

    /** Retira la justificación del jugador. */
    void retirar(Long usuarioAppId, Long partidoId, Long jugadorId);

    /** Vista del entrenador: quién ha justificado su ausencia a este partido. */
    List<JustificacionFaltaPartidoResponse> obtener(Long usuarioAppId, Long partidoId);
}
