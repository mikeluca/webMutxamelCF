package com.mikedev.mutxamelcf.dao;

import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.SolicitudFamilia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaJugador;

/**
 * Las solicitudes de acceso de las familias (SOLICITUDES_FAMILIA y SOLICITUDES_FAMILIA_JUGADOR). Aquí no hay reglas de
 * negocio: las pone {@code SolicitudFamiliaService}.
 */
public interface SolicitudFamiliaDao {

    /** Inserta la solicitud con sus hijos (todos PENDIENTE) y devuelve su id. */
    long insertar(SolicitudFamilia solicitud, List<SolicitudFamiliaJugador> hijos);

    /** Sustituye los datos escritos y los hijos de una solicitud que sigue en PENDIENTE_EMAIL (el familiar la repite). */
    void reemplazarDatos(long solicitudId, SolicitudFamilia datos, List<SolicitudFamiliaJugador> hijos);

    SolicitudFamilia obtenerPorId(long id);

    /** Las solicitudes de la cuenta, de la más reciente a la más antigua (incluidas las caducadas). */
    List<SolicitudFamilia> obtenerPorUsuario(long usuarioAppId);

    /** La solicitud de la cuenta que sigue en PENDIENTE_EMAIL o PENDIENTE (la más reciente), o null. */
    SolicitudFamilia obtenerAbiertaDeUsuario(long usuarioAppId);

    List<SolicitudFamiliaJugador> obtenerHijos(long solicitudId);

    SolicitudFamiliaJugador obtenerHijo(long hijoId);

    /** PENDIENTE_EMAIL -> PENDIENTE. Devuelve las filas afectadas (0 si ya no estaba en PENDIENTE_EMAIL). */
    int confirmarEmail(long solicitudId);

    List<SolicitudFamilia> listarPorEstado(String estado);

    int contarPorEstado(String estado);

    /** APROBADA y RECHAZADA revisadas desde {@code desde}, de la más reciente a la más antigua. */
    List<SolicitudFamilia> listarRevisadasDesde(LocalDateTime desde);

    void aprobarHijo(long hijoId, long jugadorId);

    void rechazarHijo(long hijoId);

    /** Guarda el motivo de rechazo de la solicitud (el único sitio: los hijos no tienen el suyo). */
    void actualizarMotivo(long solicitudId, String motivo);

    /** Cierra la solicitud: estado APROBADA o RECHAZADA, motivo (puede ser null), fecha y quién la revisó. */
    void cerrar(long solicitudId, String estado, String motivo, LocalDateTime fecha, Long revisadaPorId,
            String revisadaPorPanel);

    /** PENDIENTE_EMAIL o PENDIENTE solicitadas antes de {@code limite}, y RECHAZADA revisadas antes de {@code limite}. */
    List<SolicitudFamilia> listarCaducables(LocalDateTime limite);

    /** PENDIENTE solicitadas antes de {@code limite} (las que llevan días sin revisar). */
    List<SolicitudFamilia> listarPendientesAnterioresA(LocalDateTime limite);

    /** Los jugadores activos (sin baja), sin foto, para buscar coincidencias con lo que escribió la familia. */
    List<com.mikedev.mutxamelcf.model.JugadorCandidato> listarJugadoresActivos();

    /** Borra la solicitud entera: sus hijos y los datos personales del familiar (la tabla no admite una fila sin ellos). */
    void caducar(long solicitudId);
}
