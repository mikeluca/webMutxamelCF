package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.InvitacionDetalle;
import com.mikedev.mutxamelcf.model.JugadorInvitable;

/**
 * Consultas de los jugadores invitados a la convocatoria de otro equipo (docs/convocatoria-invitados.md). Las
 * escrituras van por {@link ConvocatoriaJugadorDao}.
 */
public interface InvitacionDao {

    InvitacionDetalle obtenerDetalle(Long convocatoriaJugadorId);

    /** Las invitaciones de una convocatoria en cualquier estado, por jugador. */
    List<InvitacionDetalle> porConvocatoria(Long convocatoriaId);

    /**
     * Pendientes de coordinación o de su entrenador, de partidos no cancelados con fecha desde la indicada, por fecha
     * del partido y solicitud.
     */
    List<InvitacionDetalle> pendientes(LocalDate desde);

    /**
     * Las invitaciones del jugador (todas menos las rechazadas) en partidos no cancelados de ese día, salvo la
     * indicada.
     */
    List<InvitacionDetalle> delJugadorEnDia(Long jugadorId, LocalDate dia, Long excluirConvocatoriaJugadorId);

    /** Las invitaciones AUTORIZADAS del jugador en partidos no cancelados con fecha entre las dos (incluidas). */
    List<InvitacionDetalle> autorizadasDelJugador(Long jugadorId, LocalDate desde, LocalDate hasta);

    /** Las PENDIENTE_ENTRENADOR cuya petición es de antes del límite y aún sin el aviso de las 12 horas. */
    List<InvitacionDetalle> vencidasSinAviso(LocalDateTime limite);

    /** Partidos no cancelados del equipo ese día. */
    List<PartidoDelDia> partidosDelEquipoEnDia(Long equipoId, LocalDate dia);

    /**
     * Las filas del jugador como convocado del propio equipo (sin EQUIPO_ORIGEN_ID) en convocatorias de partidos no
     * cancelados de ese equipo ese día.
     */
    List<ConvocatoriaJugador> filasPropiasDelJugadorEnDia(Long equipoId, Long jugadorId, LocalDate dia);

    /**
     * Jugadores activos del equipo en la temporada: ni de baja, ni PRUEBA y con equipo definitivo. Por dorsal y
     * apellidos.
     */
    List<JugadorInvitable> jugadoresInvitables(Long equipoId, Long temporadaId);

    /** Un partido no cancelado de un equipo (para explicar el solapamiento). */
    record PartidoDelDia(Long id, String hora, String rival) {
    }
}
