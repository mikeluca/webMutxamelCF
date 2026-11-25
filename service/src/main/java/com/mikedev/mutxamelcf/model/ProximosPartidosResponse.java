package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * "Tu próximo partido" de Inicio (docs/api-app-inicio-pendientes.md): por cada persona y equipo de la cuenta, su próximo
 * partido que aún no ha empezado en los próximos 14 días.
 */
public record ProximosPartidosResponse(List<Entrada> entradas) {

    public static final String TIPO_JUGADOR = "JUGADOR";
    public static final String TIPO_FAMILIAR = "FAMILIAR";
    public static final String TIPO_ENTRENADOR = "ENTRENADOR";

    /**
     * @param tipo          JUGADOR, FAMILIAR o ENTRENADOR
     * @param jugadorId     el jugador (solo la propia cuenta o sus hijos); null para el entrenador
     * @param nombreJugador su nombre; null para el entrenador
     * @param equipoId      el equipo de la entrada (el del jugador o el que entrena)
     * @param partido       el próximo partido (null si {@code descansa})
     * @param convocatoria  cómo está el partido para esa persona (null si {@code descansa})
     * @param descansa      el equipo no juega esa jornada (según la FFCV) ni hay otro partido en los 14 días
     */
    public record Entrada(
            String tipo,
            Long jugadorId,
            String nombreJugador,
            Long equipoId,
            String nombreEquipo,
            PartidoDTO partido,
            EstadoConvocatoria convocatoria,
            boolean descansa) {
    }

    /**
     * Estados: SIN_CONVOCATORIA, CONVOCADO, NO_CONVOCADO, AUSENCIA_JUSTIFICADA, INVITADO y, para el entrenador, PUBLICADA.
     *
     * @param equipoInvita    solo INVITADO: el equipo que lo invita
     * @param numeroConvocados solo PUBLICADA (entrenador): cuántos van convocados
     */
    public record EstadoConvocatoria(
            String estado,
            String horaCitacion,
            String lugarCitacion,
            EquipacionResponse equipaje,
            EquipacionResponse equipajeMochila,
            String equipoInvita,
            Integer numeroConvocados) {

        public static final String SIN_CONVOCATORIA = "SIN_CONVOCATORIA";
        public static final String CONVOCADO = "CONVOCADO";
        public static final String NO_CONVOCADO = "NO_CONVOCADO";
        public static final String AUSENCIA_JUSTIFICADA = "AUSENCIA_JUSTIFICADA";
        public static final String INVITADO = "INVITADO";
        public static final String PUBLICADA = "PUBLICADA";

        public static EstadoConvocatoria de(String estado) {
            return new EstadoConvocatoria(estado, null, null, null, null, null, null);
        }
    }
}
