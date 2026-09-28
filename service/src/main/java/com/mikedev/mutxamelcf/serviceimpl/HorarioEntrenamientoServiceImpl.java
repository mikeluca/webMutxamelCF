package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.HorarioEntrenamientoDao;
import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.HorarioEntrenamientoService;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

@Service
public class HorarioEntrenamientoServiceImpl implements HorarioEntrenamientoService {

    /*
     * Ventana de generación: cada alta/edición de un horario mantiene
     * sus sesiones generadas hasta esta distancia en el futuro. El job
     * mensual programado (ver generarSesionesParaTodosLosHorarios) hace
     * lo mismo para todos los horarios activos, para que la ventana no
     * se quede corta con el paso del tiempo aunque nadie vuelva a tocar
     * el horario.
     */
    private static final long MESES_VENTANA_GENERACION = 2;

    private final HorarioEntrenamientoDao horarioEntrenamientoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final SesionEntrenamientoService sesionEntrenamientoService;

    public HorarioEntrenamientoServiceImpl(
            HorarioEntrenamientoDao horarioEntrenamientoDao,
            EquipoGestionDao equipoGestionDao,
            SesionEntrenamientoService sesionEntrenamientoService) {

        this.horarioEntrenamientoDao = horarioEntrenamientoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.sesionEntrenamientoService = sesionEntrenamientoService;
    }

    @Override
    @Transactional
    public HorarioEntrenamientoResponse crear(Long usuarioAppId, HorarioEntrenamientoCrearRequest request) {

        validarUsuario(usuarioAppId);
        validarRequest(request);

        if (!equipoGestionDao.existeEquipo(request.getEquipoId())) {
            throw new IllegalArgumentException("El equipo no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, request.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        HorarioEntrenamiento horario = new HorarioEntrenamiento();

        horario.setEquipoId(request.getEquipoId());
        horario.setDiaSemana(request.getDiaSemana());
        horario.setHora(request.getHora());
        horario.setLugar(limpiar(request.getLugar()));
        horario.setActivo(true);
        horario.setUsuarioActualizoId(usuarioAppId);
        horario.setFechaActualizacion(Timestamp.valueOf(LocalDateTime.now()));

        horario = horarioEntrenamientoDao.crear(horario);

        sesionEntrenamientoService.generarSesiones(horario.getId(), ventanaGeneracion());

        return construirResponse(horario);
    }

    @Override
    @Transactional
    public HorarioEntrenamientoResponse actualizar(Long usuarioAppId, Long horarioId,
            HorarioEntrenamientoActualizarRequest request) {

        validarUsuario(usuarioAppId);

        if (horarioId == null || horarioId <= 0) {
            throw new IllegalArgumentException("El ID del horario no es válido");
        }

        validarRequestActualizacion(request);

        HorarioEntrenamiento horario = horarioEntrenamientoDao.obtenerPorId(horarioId);

        if (horario == null) {
            throw new IllegalArgumentException("El horario no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, horario.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        boolean diaSemanaCambia = !request.getDiaSemana().equals(horario.getDiaSemana());
        boolean seguiraActivo = Boolean.TRUE.equals(request.getActivo());

        horario.setDiaSemana(request.getDiaSemana());
        horario.setHora(request.getHora());
        horario.setLugar(limpiar(request.getLugar()));
        horario.setActivo(seguiraActivo);
        horario.setUsuarioActualizoId(usuarioAppId);
        horario.setFechaActualizacion(Timestamp.valueOf(LocalDateTime.now()));

        horarioEntrenamientoDao.actualizar(horario);

        /*
         * Decisión de diseño (a confirmar con el cliente): si el
         * horario se desactiva, o si cambia de día mientras sigue
         * activo, las sesiones futuras PROGRAMADA generadas a partir de
         * él ya no representan el horario real, así que se cancelan.
         * Si solo cambian hora/lugar (mismo día), las sesiones futuras
         * YA generadas NO se tocan (conservan su hora/lugar de cuando
         * se generaron); el entrenador puede editarlas una a una con
         * PUT /sesiones-entrenamiento/{id} si quiere igualarlas. Si
         * sigue activo, siempre se llama a generarSesiones() para
         * rellenar/mantener la ventana de 2 meses.
         */
        if (!seguiraActivo) {

            sesionEntrenamientoService.cancelarFuturasPorHorario(horarioId);

        } else if (diaSemanaCambia) {

            sesionEntrenamientoService.cancelarFuturasPorHorario(horarioId);
            sesionEntrenamientoService.generarSesiones(horarioId, ventanaGeneracion());

        } else {

            sesionEntrenamientoService.generarSesiones(horarioId, ventanaGeneracion());
        }

        return construirResponse(horario);
    }

    @Override
    @Transactional
    public void eliminar(Long usuarioAppId, Long horarioId) {

        validarUsuario(usuarioAppId);

        if (horarioId == null || horarioId <= 0) {
            throw new IllegalArgumentException("El ID del horario no es válido");
        }

        HorarioEntrenamiento horario = horarioEntrenamientoDao.obtenerPorId(horarioId);

        if (horario == null) {
            throw new IllegalArgumentException("El horario no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, horario.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        horario.setActivo(false);
        horario.setUsuarioActualizoId(usuarioAppId);
        horario.setFechaActualizacion(Timestamp.valueOf(LocalDateTime.now()));

        horarioEntrenamientoDao.actualizar(horario);

        sesionEntrenamientoService.cancelarFuturasPorHorario(horarioId);
    }

    @Override
    public List<HorarioEntrenamientoResponse> obtenerActivosPorEquipo(Long usuarioAppId, Long equipoId) {

        validarUsuario(usuarioAppId);

        if (equipoId == null || equipoId <= 0) {
            throw new IllegalArgumentException("El ID del equipo no es válido");
        }

        if (!equipoGestionDao.existeEquipo(equipoId)) {
            throw new IllegalArgumentException("El equipo no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
            throw new SecurityException("No tienes permiso para consultar este equipo");
        }

        List<HorarioEntrenamiento> horarios = horarioEntrenamientoDao.obtenerActivosPorEquipo(equipoId);

        List<HorarioEntrenamientoResponse> respuesta = new ArrayList<>();

        for (HorarioEntrenamiento horario : horarios) {
            respuesta.add(construirResponse(horario));
        }

        return respuesta;
    }

    private LocalDate ventanaGeneracion() {
        return LocalDate.now().plusMonths(MESES_VENTANA_GENERACION);
    }

    private HorarioEntrenamientoResponse construirResponse(HorarioEntrenamiento horario) {

        HorarioEntrenamientoResponse response = new HorarioEntrenamientoResponse();

        response.setId(horario.getId());
        response.setEquipoId(horario.getEquipoId());
        response.setEquipo(equipoGestionDao.obtenerNombreEquipo(horario.getEquipoId()));
        response.setDiaSemana(horario.getDiaSemana());
        response.setHora(horario.getHora());
        response.setLugar(horario.getLugar());
        response.setActivo(horario.isActivo());

        return response;
    }

    private void validarRequest(HorarioEntrenamientoCrearRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("La petición no puede ser nula");
        }

        if (request.getEquipoId() == null) {
            throw new IllegalArgumentException("El equipo es obligatorio");
        }

        validarDiaSemana(request.getDiaSemana());

        if (request.getHora() == null || request.getHora().isBlank()) {
            throw new IllegalArgumentException("La hora es obligatoria");
        }
    }

    private void validarRequestActualizacion(HorarioEntrenamientoActualizarRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("La petición no puede ser nula");
        }

        validarDiaSemana(request.getDiaSemana());

        if (request.getHora() == null || request.getHora().isBlank()) {
            throw new IllegalArgumentException("La hora es obligatoria");
        }

        if (request.getActivo() == null) {
            throw new IllegalArgumentException("El campo activo es obligatorio");
        }
    }

    private void validarDiaSemana(Integer diaSemana) {

        if (diaSemana == null || diaSemana < 1 || diaSemana > 7) {
            throw new IllegalArgumentException("El día de la semana debe estar entre 1 (lunes) y 7 (domingo)");
        }
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
