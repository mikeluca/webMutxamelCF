package com.mikedev.mutxamelcf.serviceimpl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.HorarioEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.SesionEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;
import com.mikedev.mutxamelcf.model.JustificacionFaltaEntrenamiento;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaResponse;
import com.mikedev.mutxamelcf.model.SesionEntrenamiento;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.EntrenamientoService;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

@Service
public class SesionEntrenamientoServiceImpl implements SesionEntrenamientoService {

    private static final String ESTADO_FALTA_JUSTIFICADA = "FALTA_JUSTIFICADA";

    private final SesionEntrenamientoDao sesionEntrenamientoDao;
    private final HorarioEntrenamientoDao horarioEntrenamientoDao;
    private final JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final UsuarioAppVinculoDao usuarioAppVinculoDao;
    private final ComunicacionService comunicacionService;
    private final EntrenamientoService entrenamientoService;

    public SesionEntrenamientoServiceImpl(
            SesionEntrenamientoDao sesionEntrenamientoDao,
            HorarioEntrenamientoDao horarioEntrenamientoDao,
            JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao,
            EquipoGestionDao equipoGestionDao,
            UsuarioAppVinculoDao usuarioAppVinculoDao,
            ComunicacionService comunicacionService,
            EntrenamientoService entrenamientoService) {

        this.sesionEntrenamientoDao = sesionEntrenamientoDao;
        this.horarioEntrenamientoDao = horarioEntrenamientoDao;
        this.justificacionFaltaEntrenamientoDao = justificacionFaltaEntrenamientoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.usuarioAppVinculoDao = usuarioAppVinculoDao;
        this.comunicacionService = comunicacionService;
        this.entrenamientoService = entrenamientoService;
    }

    @Override
    @Transactional
    public void generarSesiones(Long horarioId, LocalDate hasta) {

        HorarioEntrenamiento horario = horarioEntrenamientoDao.obtenerPorId(horarioId);

        if (horario == null || !horario.isActivo()) {
            return;
        }

        if (hasta == null) {
            return;
        }

        LocalDate ultimaGenerada = sesionEntrenamientoDao.obtenerUltimaFechaGenerada(horarioId);

        LocalDate hoy = LocalDate.now();

        LocalDate desde = ultimaGenerada != null && ultimaGenerada.plusDays(1).isAfter(hoy)
                ? ultimaGenerada.plusDays(1)
                : hoy;

        if (desde.isAfter(hasta)) {
            return;
        }

        DayOfWeek diaSemana = DayOfWeek.of(horario.getDiaSemana());

        LocalDate fecha = primeraFechaConDia(desde, diaSemana);

        while (!fecha.isAfter(hasta)) {

            if (!sesionEntrenamientoDao.existePorHorarioYFecha(horarioId, fecha)) {

                SesionEntrenamiento sesion = new SesionEntrenamiento();

                sesion.setEquipoId(horario.getEquipoId());
                sesion.setHorarioId(horarioId);
                sesion.setFecha(fecha);
                sesion.setHora(horario.getHora());
                sesion.setLugar(horario.getLugar());
                sesion.setEstado(SesionEntrenamiento.ESTADO_PROGRAMADA);

                sesionEntrenamientoDao.crear(sesion);

                entrenamientoService.crearAutomaticoParaSesion(
                        sesion.getId(),
                        sesion.getEquipoId(),
                        sesion.getFecha(),
                        resolverUsuarioAutorHorario(horario));
            }

            fecha = fecha.plusWeeks(1);
        }
    }

    private static LocalDate primeraFechaConDia(LocalDate desde, DayOfWeek diaSemana) {

        int diferencia = (diaSemana.getValue() - desde.getDayOfWeek().getValue() + 7) % 7;

        return desde.plusDays(diferencia);
    }

    /**
     * El ENTRENAMIENTO automático necesita un autor (USUARIO_ENTRENADOR_ID
     * es obligatorio). Como la generación de sesiones puede dispararse sin
     * ningún usuario en contexto (job mensual programado), usamos, en este
     * orden, el mismo criterio que RecordatorioResultadoPartidoScheduler
     * para su problema análogo: quien tocó por última vez este horario y,
     * si no hay ninguno, el primer coordinador disponible.
     */
    private Long resolverUsuarioAutorHorario(HorarioEntrenamiento horario) {

        if (horario.getUsuarioActualizoId() != null) {
            return horario.getUsuarioActualizoId();
        }

        List<Long> coordinadores = equipoGestionDao.obtenerCoordinadores();

        return coordinadores.isEmpty() ? null : coordinadores.get(0);
    }

    @Override
    @Transactional
    public void generarSesionesParaTodosLosHorarios() {

        LocalDate hasta = LocalDate.now().plusMonths(2);

        for (HorarioEntrenamiento horario : horarioEntrenamientoDao.obtenerTodosActivos()) {
            generarSesiones(horario.getId(), hasta);
        }
    }

    @Override
    @Transactional
    public void cancelarFuturasPorHorario(Long horarioId) {

        LocalDate hoy = LocalDate.now();

        /*
         * Necesitamos saber QUÉ sesiones se van a eliminar para poder
         * borrar antes lo que las referencia: FK_ENTRENAMIENTOS_SESION y
         * FK_JUSTIF_FALTA_SESION impiden eliminar una sesión mientras
         * exista un ENTRENAMIENTO o una JUSTIFICACION_FALTA que la
         * referencien (ninguna de las dos FK tiene ON DELETE CASCADE).
         * Si una familia ya justificó una falta en alguna de estas
         * sesiones y no se borrara antes, Oracle rechazaría el DELETE de
         * la sesión con ORA-02292 y toda la operación (incluida la
         * desactivación del horario) se revertiría.
         */
        List<SesionEntrenamiento> sesionesAEliminar = sesionEntrenamientoDao
                .obtenerFuturasProgramadasPorHorario(horarioId, hoy);

        List<Long> idsSesionesAEliminar = sesionesAEliminar.stream()
                .map(SesionEntrenamiento::getId)
                .toList();

        justificacionFaltaEntrenamientoDao.eliminarPorSesionIds(idsSesionesAEliminar);

        for (SesionEntrenamiento sesion : sesionesAEliminar) {
            entrenamientoService.eliminarPorSesionEntrenamientoId(sesion.getId());
        }

        sesionEntrenamientoDao.eliminarFuturasProgramadasPorHorario(horarioId, hoy);
    }

    @Override
    @Transactional
    public SesionEntrenamientoResponse crear(Long usuarioAppId, SesionEntrenamientoCrearRequest request) {

        validarUsuario(usuarioAppId);

        if (request == null) {
            throw new IllegalArgumentException("La petición no puede ser nula");
        }

        if (request.getEquipoId() == null) {
            throw new IllegalArgumentException("El equipo es obligatorio");
        }

        if (request.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }

        if (!equipoGestionDao.existeEquipo(request.getEquipoId())) {
            throw new IllegalArgumentException("El equipo no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, request.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        SesionEntrenamiento sesion = new SesionEntrenamiento();

        sesion.setEquipoId(request.getEquipoId());
        sesion.setHorarioId(null);
        sesion.setFecha(request.getFecha());
        sesion.setHora(limpiar(request.getHora()));
        sesion.setLugar(limpiar(request.getLugar()));
        sesion.setEstado(SesionEntrenamiento.ESTADO_PROGRAMADA);

        sesion = sesionEntrenamientoDao.crear(sesion);

        entrenamientoService.crearAutomaticoParaSesion(
                sesion.getId(),
                sesion.getEquipoId(),
                sesion.getFecha(),
                usuarioAppId);

        return construirResponse(sesion);
    }

    @Override
    @Transactional
    public SesionEntrenamientoResponse actualizar(Long usuarioAppId, Long sesionId,
            SesionEntrenamientoActualizarRequest request) {

        validarUsuario(usuarioAppId);

        if (sesionId == null) {
            throw new IllegalArgumentException("El ID de la sesión no es válido");
        }

        SesionEntrenamiento sesion = sesionEntrenamientoDao.obtenerPorId(sesionId);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, sesion.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        if (request != null) {
            sesion.setHora(limpiar(request.getHora()));
            sesion.setLugar(limpiar(request.getLugar()));
        }

        sesionEntrenamientoDao.actualizar(sesion);

        return construirResponse(sesion);
    }

    @Override
    @Transactional
    public void cancelar(Long usuarioAppId, Long sesionId, String motivo) {

        validarUsuario(usuarioAppId);

        if (sesionId == null) {
            throw new IllegalArgumentException("El ID de la sesión no es válido");
        }

        String motivoLimpio = limpiar(motivo);

        if (motivoLimpio == null) {
            throw new IllegalArgumentException("El motivo de la cancelación es obligatorio");
        }

        SesionEntrenamiento sesion = sesionEntrenamientoDao.obtenerPorId(sesionId);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, sesion.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        sesionEntrenamientoDao.cancelar(sesionId, motivoLimpio);

        sesion.setMotivoCancelacion(motivoLimpio);

        entrenamientoService.eliminarPorSesionEntrenamientoId(sesionId);

        generarNotificacionesCancelacion(sesion, usuarioAppId);
    }

    private void generarNotificacionesCancelacion(SesionEntrenamiento sesion, Long usuarioAppId) {

        String equipo = equipoGestionDao.obtenerNombreEquipo(sesion.getEquipoId());

        String fecha = sesion.getFecha() != null ? sesion.getFecha().toString() : "";

        String titulo = "Entrenamiento cancelado";

        String mensaje = "El entrenamiento del equipo "
                + equipo
                + " del día "
                + fecha
                + " ha sido cancelado. Motivo: "
                + sesion.getMotivoCancelacion()
                + ".";

        List<Long> jugadoresEquipo = equipoGestionDao.obtenerJugadoresPorEquipo(sesion.getEquipoId());

        for (Long jugadorId : jugadoresEquipo) {

            List<Long> usuariosDirectos = equipoGestionDao.obtenerUsuariosPorJugador(jugadorId);
            List<Long> usuariosFamiliares = equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(jugadorId);

            Set<Long> destinatariosUnicos = new HashSet<>();
            destinatariosUnicos.addAll(usuariosDirectos);
            destinatariosUnicos.addAll(usuariosFamiliares);

            for (Long usuarioId : destinatariosUnicos) {

                Comunicacion comunicacion = new Comunicacion();
                comunicacion.setTitulo(titulo);
                comunicacion.setContenido(mensaje);

                comunicacionService.crearPrivada(
                        comunicacion,
                        List.of(usuarioId),
                        usuarioAppId);
            }
        }
    }

    @Override
    public SesionEntrenamientoResponse obtenerPorId(Long sesionId) {

        if (sesionId == null) {
            throw new IllegalArgumentException("El ID de la sesión no es válido");
        }

        SesionEntrenamiento sesion = sesionEntrenamientoDao.obtenerPorId(sesionId);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión no existe");
        }

        return construirResponse(sesion);
    }

    @Override
    public List<SesionEntrenamientoResponse> obtenerPorEquipoYRango(Long equipoId, LocalDate desde, LocalDate hasta) {

        if (equipoId == null) {
            throw new IllegalArgumentException("El equipo es obligatorio");
        }

        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("El rango de fechas es obligatorio");
        }

        List<SesionEntrenamiento> sesiones = sesionEntrenamientoDao.obtenerPorEquipoYRango(equipoId, desde, hasta);

        List<SesionEntrenamientoResponse> respuesta = new ArrayList<>();

        for (SesionEntrenamiento sesion : sesiones) {
            respuesta.add(construirResponse(sesion));
        }

        return respuesta;
    }

    @Override
    public List<SesionEntrenamientoResponse> obtenerPorEquipoYRangoParaJugador(
            Long usuarioAppId, Long equipoId, LocalDate desde, LocalDate hasta, Long jugadorId) {

        validarUsuario(usuarioAppId);

        if (jugadorId == null) {
            throw new IllegalArgumentException("El jugador es obligatorio");
        }

        if (!equipoGestionDao.perteneceJugadorAEquipo(jugadorId, equipoId)) {
            throw new IllegalArgumentException("El jugador no pertenece a este equipo");
        }

        if (!usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
            throw new SecurityException("No tienes permiso para consultar este jugador");
        }

        List<SesionEntrenamientoResponse> respuesta = obtenerPorEquipoYRango(equipoId, desde, hasta);

        for (SesionEntrenamientoResponse sesion : respuesta) {

            JustificacionFaltaEntrenamiento justificacion = justificacionFaltaEntrenamientoDao
                    .obtenerPorSesionYJugador(sesion.getId(), jugadorId);

            if (justificacion != null) {
                sesion.setJustificado(true);
                sesion.setMotivoJustificacion(justificacion.getMotivo());
            }
        }

        return respuesta;
    }

    @Override
    @Transactional
    public JustificacionFaltaResponse justificar(Long usuarioAppId, Long sesionId, JustificacionFaltaRequest request) {

        validarUsuario(usuarioAppId);

        if (sesionId == null) {
            throw new IllegalArgumentException("El ID de la sesión no es válido");
        }

        if (request == null || request.getJugadorId() == null) {
            throw new IllegalArgumentException("El jugador es obligatorio");
        }

        SesionEntrenamiento sesion = sesionEntrenamientoDao.obtenerPorId(sesionId);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión no existe");
        }

        if (SesionEntrenamiento.ESTADO_CANCELADA.equals(sesion.getEstado())) {
            throw new IllegalArgumentException("No se puede justificar una falta para un entrenamiento cancelado");
        }

        if (sesion.getFecha() != null && sesion.getFecha().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede justificar una falta para un entrenamiento ya pasado");
        }

        if (!equipoGestionDao.perteneceJugadorAEquipo(request.getJugadorId(), sesion.getEquipoId())) {
            throw new IllegalArgumentException("El jugador no pertenece al equipo de este entrenamiento");
        }

        if (!usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), request.getJugadorId())) {
            throw new SecurityException("No tienes permiso para justificar la falta de este jugador");
        }

        JustificacionFaltaEntrenamiento existente = justificacionFaltaEntrenamientoDao.obtenerPorSesionYJugador(
                sesionId, request.getJugadorId());

        String motivo = limpiar(request.getMotivo());

        JustificacionFaltaResponse respuesta;

        if (existente != null) {

            existente.setMotivo(motivo);
            existente.setUsuarioAppId(usuarioAppId);

            justificacionFaltaEntrenamientoDao.actualizar(existente);

            respuesta = construirResponseJustificacion(existente);

        } else {

            JustificacionFaltaEntrenamiento justificacion = new JustificacionFaltaEntrenamiento();

            justificacion.setSesionId(sesionId);
            justificacion.setJugadorId(request.getJugadorId());
            justificacion.setUsuarioAppId(usuarioAppId);
            justificacion.setMotivo(motivo);

            justificacion = justificacionFaltaEntrenamientoDao.crear(justificacion);

            respuesta = construirResponseJustificacion(justificacion);
        }

        /*
         * Sincronización best-effort del registro de asistencia (ver
         * EntrenamientoService.sincronizarEstadoPorJustificacion): la
         * justificación en sí ya ha quedado guardada por encima, así que
         * esto nunca debe hacer fallar la petición.
         */
        entrenamientoService.sincronizarEstadoPorJustificacion(
                sesionId, request.getJugadorId(), ESTADO_FALTA_JUSTIFICADA);

        return respuesta;
    }

    @Override
    public List<JustificacionFaltaResponse> obtenerJustificaciones(Long usuarioAppId, Long sesionId) {

        validarUsuario(usuarioAppId);

        if (sesionId == null) {
            throw new IllegalArgumentException("El ID de la sesión no es válido");
        }

        SesionEntrenamiento sesion = sesionEntrenamientoDao.obtenerPorId(sesionId);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, sesion.getEquipoId())) {
            throw new SecurityException("No tienes permiso para consultar las justificaciones de este equipo");
        }

        List<JustificacionFaltaEntrenamiento> justificaciones = justificacionFaltaEntrenamientoDao.obtenerPorSesion(
                sesionId);

        List<JustificacionFaltaResponse> respuesta = new ArrayList<>();

        for (JustificacionFaltaEntrenamiento justificacion : justificaciones) {
            respuesta.add(construirResponseJustificacion(justificacion));
        }

        return respuesta;
    }

    private SesionEntrenamientoResponse construirResponse(SesionEntrenamiento sesion) {

        SesionEntrenamientoResponse response = new SesionEntrenamientoResponse();

        response.setId(sesion.getId());
        response.setEquipoId(sesion.getEquipoId());
        response.setEquipo(equipoGestionDao.obtenerNombreEquipo(sesion.getEquipoId()));
        response.setHorarioId(sesion.getHorarioId());
        response.setFecha(sesion.getFecha());
        response.setHora(sesion.getHora());
        response.setLugar(sesion.getLugar());
        response.setEstado(sesion.getEstado());
        response.setMotivoCancelacion(sesion.getMotivoCancelacion());

        return response;
    }

    private JustificacionFaltaResponse construirResponseJustificacion(JustificacionFaltaEntrenamiento justificacion) {

        JustificacionFaltaResponse response = new JustificacionFaltaResponse();

        response.setId(justificacion.getId());
        response.setSesionId(justificacion.getSesionId());
        response.setJugadorId(justificacion.getJugadorId());
        response.setJugador(equipoGestionDao.obtenerNombreJugador(justificacion.getJugadorId()));
        response.setUsuarioAppId(justificacion.getUsuarioAppId());
        response.setMotivo(justificacion.getMotivo());
        response.setFechaCreacion(justificacion.getFechaCreacion());

        return response;
    }

    private void validarUsuario(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }
    }

    private String limpiar(String valor) {

        if (valor == null) {
            return null;
        }

        String resultado = valor.trim();

        return resultado.isEmpty() ? null : resultado;
    }

}
