package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una invitación con los datos de su jugador, su equipo de origen y el partido de destino (para la lista de
 * coordinación, los avisos y el calendario).
 *
 * @param id                     CONVOCATORIA_JUGADOR.ID
 * @param equipoDestinoId        equipo del partido
 * @param usuarioInvitaId        quién invitó; si la fila no lo guarda, el de la convocatoria
 * @param cancelado              el partido está cancelado
 */
public record InvitacionDetalle(
        Long id,
        Long convocatoriaId,
        Long jugadorId,
        String jugador,
        Long equipoOrigenId,
        String equipoOrigen,
        EstadoInvitacion estado,
        Long equipoDestinoId,
        String equipoDestino,
        Long partidoId,
        LocalDate dia,
        String hora,
        String rival,
        boolean cancelado,
        Long usuarioInvitaId,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaPeticionEntrenador,
        boolean aviso12hEnviado,
        String motivoRechazo) {
}
