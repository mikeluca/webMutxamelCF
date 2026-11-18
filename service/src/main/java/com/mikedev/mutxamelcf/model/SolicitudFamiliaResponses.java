package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Las respuestas de las solicitudes de familia. Hay dos vistas y no se mezclan:
 * <ul>
 * <li>{@link Mia}: lo que ve el propio familiar. NUNCA lleva jugadores ni coincidencias, solo lo que él escribió y el
 * estado de cada hijo.</li>
 * <li>{@link Revision}: lo que ve quien revisa (coordinación, administración y los entrenadores de los equipos
 * coincidentes), con las coincidencias.</li>
 * </ul>
 */
public final class SolicitudFamiliaResponses {

    private SolicitudFamiliaResponses() {
    }

    /** Un hijo en la vista del familiar. */
    public record HijoMio(Long id, String nombre, String apellidos, String equipo, String estado) {
    }

    /** Una solicitud en la vista del familiar. */
    public record Mia(Long id, String estado, String parentesco, LocalDateTime fechaSolicitud,
            LocalDateTime fechaRevision, String motivoRechazo, List<HijoMio> hijos) {
    }

    /** Un jugador que coincide con lo que escribió la familia. */
    public record Coincidencia(Long jugadorId, String nombre, Long equipoId, String equipo, boolean coincideEquipo) {
    }

    /** Un hijo en la vista de quien revisa; {@code jugador} es el elegido al aprobar. */
    public record HijoRevision(Long id, String nombre, String apellidos, String equipo, String estado,
            Coincidencia jugador, List<Coincidencia> coincidencias) {
    }

    /** Una solicitud en la vista de quien revisa. */
    public record Revision(Long id, String nombre, String apellidos, String email, String telefono, String parentesco,
            String estado, String motivoRechazo, LocalDateTime fechaSolicitud, LocalDateTime fechaRevision,
            List<HijoRevision> hijos) {
    }

    /** Lo que hace falta para avisar a un familiar de que su solicitud ha caducado y se ha borrado. */
    public record Caducada(String email, String nombre, String idioma) {
    }
}
