package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una invitación pendiente en la lista de coordinación (docs/api-app-convocatoria-invitados.md).
 *
 * @param estado                   PENDIENTE_COORDINACION o PENDIENTE_ENTRENADOR
 * @param convoca                  quién invitó (su nombre o, si la cuenta no lo tiene, el cuerpo técnico del equipo)
 * @param plazoVencido             PENDIENTE_ENTRENADOR con más de 12 horas desde que se le pidió
 * @param jugadorConvocadoEnOrigen su entrenador lo tiene convocado en uno de esos partidos
 * @param solapamiento             partidos no cancelados de su equipo de origen ese día (vacío = no hay solapamiento)
 * @param otraInvitacion           otra invitación suya ese día en otro partido, si la hay
 */
public record InvitacionPendienteResponse(
        Long id,
        String estado,
        Long convocatoriaId,
        Long partidoId,
        Long jugadorId,
        String jugador,
        Long equipoOrigenId,
        String equipoOrigen,
        Long equipoDestinoId,
        String equipoDestino,
        LocalDate fechaPartido,
        String horaPartido,
        String rival,
        Long usuarioInvitaId,
        String convoca,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaPeticionEntrenador,
        boolean plazoVencido,
        boolean jugadorConvocadoEnOrigen,
        List<PartidoSolapado> solapamiento,
        OtraInvitacion otraInvitacion) {

    /** Un partido de su equipo de origen que se juega el mismo día. */
    public record PartidoSolapado(Long partidoId, String hora, String rival) {
    }

    /** Su otra invitación ese día: el equipo, el partido y en qué estado está. */
    public record OtraInvitacion(
            Long convocatoriaJugadorId,
            Long equipoDestinoId,
            String equipoDestino,
            Long partidoId,
            String horaPartido,
            String rival,
            String estado) {
    }
}
