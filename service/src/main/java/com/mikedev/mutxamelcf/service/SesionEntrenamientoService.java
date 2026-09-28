package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaResponse;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoResponse;

public interface SesionEntrenamientoService {

    /**
     * Genera las sesiones de un horario recurrente entre
     * max(hoy, última fecha ya generada + 1) y hasta (ambos incluidos),
     * una por cada fecha cuyo día de la semana coincide con el del
     * horario. Idempotente: no duplica sesiones ya generadas para una
     * misma fecha. No hace nada si el horario no existe o está inactivo.
     */
    void generarSesiones(Long horarioId, LocalDate hasta);

    /**
     * Recorre todos los horarios activos de todos los equipos y
     * mantiene su ventana de generación hasta hoy + 2 meses. Pensado
     * para el job mensual programado.
     */
    void generarSesionesParaTodosLosHorarios();

    /**
     * Cancela (ESTADO = CANCELADA) todas las sesiones futuras que
     * siguen PROGRAMADA de un horario, sin tocar las pasadas.
     */
    void cancelarFuturasPorHorario(Long horarioId);

    /**
     * Alta de una sesión suelta, no ligada a ningún horario recurrente.
     */
    SesionEntrenamientoResponse crear(Long usuarioAppId, SesionEntrenamientoCrearRequest request);

    /**
     * Edita hora/lugar de una única sesión (no afecta al resto de
     * sesiones generadas a partir del mismo horario, si lo tuviera).
     */
    SesionEntrenamientoResponse actualizar(Long usuarioAppId, Long sesionId,
            SesionEntrenamientoActualizarRequest request);

    /**
     * Cancela una sesión. El motivo es obligatorio: se guarda y se
     * incluye en la notificación a jugadores/familias.
     */
    void cancelar(Long usuarioAppId, Long sesionId, String motivo);

    SesionEntrenamientoResponse obtenerPorId(Long sesionId);

    List<SesionEntrenamientoResponse> obtenerPorEquipoYRango(Long equipoId, LocalDate desde, LocalDate hasta);

    /**
     * Igual que {@link #obtenerPorEquipoYRango}, pero además marca en
     * cada sesión si el jugador indicado ya ha justificado su falta
     * (vista jugador/familiar). El usuarioAppId debe estar vinculado a
     * ese jugador (como jugador o como familiar).
     */
    List<SesionEntrenamientoResponse> obtenerPorEquipoYRangoParaJugador(
            Long usuarioAppId, Long equipoId, LocalDate desde, LocalDate hasta, Long jugadorId);

    /**
     * Alta/actualización (upsert) de una justificación de falta
     * informativa. El usuarioAppId debe estar vinculado al jugador
     * (como jugador o como familiar), y la sesión debe ser futura y no
     * estar cancelada.
     */
    JustificacionFaltaResponse justificar(Long usuarioAppId, Long sesionId, JustificacionFaltaRequest request);

    /**
     * Listado de justificaciones de una sesión, para la vista del
     * entrenador. Requiere poder gestionar el equipo de la sesión.
     */
    List<JustificacionFaltaResponse> obtenerJustificaciones(Long usuarioAppId, Long sesionId);

}
