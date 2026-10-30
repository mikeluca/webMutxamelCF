package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.EquipoInvitableResponse;
import com.mikedev.mutxamelcf.model.InvitacionPendienteResponse;
import com.mikedev.mutxamelcf.model.InvitacionResponse;
import com.mikedev.mutxamelcf.model.JugadorInvitableResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.Partido;

/**
 * Jugadores de otros equipos invitados a una convocatoria (docs/convocatoria-invitados.md): quién se puede invitar, el
 * visto bueno de coordinación, la liberación por su entrenador, la desestimación de la familia y los avisos.
 *
 * <p>Los errores siguen el resto de servicios: {@link SecurityException} (403), {@link IllegalArgumentException} (400) y
 * {@link com.mikedev.mutxamelcf.model.ConflictoInvitacionException} (409 con código).
 */
public interface InvitadosService {

    // ------------------------------------------------------------------ para la pantalla de la convocatoria

    /** Equipos de los que el equipo del partido puede invitar jugadores. Lo ve quien gestiona el equipo del partido. */
    List<EquipoInvitableResponse> equiposInvitables(Long usuarioAppId, Long partidoId);

    /** Jugadores activos de un equipo de origen permitido, con los que ya no se pueden invitar marcados. */
    List<JugadorInvitableResponse> jugadoresInvitables(Long usuarioAppId, Long partidoId, Long equipoOrigenId);

    // ------------------------------------------------------------------ coordinación

    /** Pendientes de coordinación o de su entrenador. Solo COORDINADOR y ADMIN_APP. */
    List<InvitacionPendienteResponse> pendientes(Long usuarioAppId);

    InvitacionResponse aprobar(Long usuarioAppId, Long convocatoriaJugadorId);

    InvitacionResponse rechazar(Long usuarioAppId, Long convocatoriaJugadorId, String motivo);

    /**
     * Libera a un jugador pendiente de su entrenador: lo quita de la convocatoria de su equipo de origen ese día y
     * queda autorizado. Lo hace el cuerpo técnico del equipo de origen o coordinación (que además avisa a ese cuerpo
     * técnico).
     */
    InvitacionResponse liberar(Long usuarioAppId, Long convocatoriaJugadorId);

    // ------------------------------------------------------------------ familia y jugador

    /** La familia o el jugador desestiman al invitado autorizado: es una justificación de ausencia a ese partido. */
    JustificacionFaltaPartidoResponse desestimar(Long usuarioAppId, Long partidoId, Long jugadorId, String motivo);

    // ------------------------------------------------------------------ enganches de la convocatoria

    /**
     * Deja la convocatoria con exactamente esos invitados: da de alta los nuevos, quita los que sobran y avisa. Con
     * {@code invitadosIds == null} no toca nada.
     */
    void sincronizarInvitados(Long usuarioAppId, Convocatoria convocatoria, Partido partido, List<Long> invitadosIds);

    /** Los invitados de la convocatoria que todavía no juegan (pendientes o rechazados), para la respuesta de la API. */
    List<ConvocatoriaJugadorResponse> invitadosSinAutorizar(Long convocatoriaId);

    /** 409 si alguno ya está autorizado como invitado con otro equipo el día del partido. */
    void comprobarNoInvitadoEseDia(Collection<Long> jugadoresIds, Partido partido);

    /** 400 si la convocatoria tiene invitados (sin rechazar): hay que quitarlos antes de cambiarle el partido. */
    void comprobarSinInvitadosAlCambiarPartido(Long convocatoriaId);

    /**
     * El cuerpo técnico del equipo de origen ha quitado a estos jugadores de su convocatoria de ese día: las
     * invitaciones pendientes de su entrenador quedan autorizadas.
     */
    void alQuitarJugadoresPropios(Long usuarioAppId, Long equipoOrigenId, LocalDate dia, Collection<Long> jugadoresIds);

    /** Se va a borrar la convocatoria: avisa a los invitados que lo necesiten. */
    void alEliminarConvocatoria(Convocatoria convocatoria, Partido partido);

    /** Se va a cancelar o borrar el partido: avisa a los invitados de su convocatoria. */
    void alCancelarPartido(Partido partido);

    // ------------------------------------------------------------------ calendario y plazo

    /** Partidos de otros equipos en los que el jugador está autorizado como invitado, con fecha entre las dos. */
    List<Partido> partidosComoInvitado(Long jugadorId, LocalDate desde, LocalDate hasta);

    /** Avisa a coordinación (una sola vez) de las invitaciones que llevan más de 12 horas pendientes de su entrenador. */
    int avisarPlazosVencidos();
}
