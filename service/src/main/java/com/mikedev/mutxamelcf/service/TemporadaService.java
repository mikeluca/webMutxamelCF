package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.TemporadaDTO;

public interface TemporadaService {

    boolean guardarTemporada(TemporadaDTO temporada);

    TemporadaDTO obtenerPorId(Long id);

    TemporadaDTO obtenerTemporadaActiva();

    /**
     * Último día de la temporada activa, o {@code null} si no hay
     * ninguna temporada activa (o no tiene fecha de fin). Es el tope
     * hasta el que se pueden crear entrenamientos.
     */
    java.time.LocalDate obtenerFechaFinTemporadaActiva();

    List<TemporadaDTO> obtenerTodos();

    /**
     * Borra la temporada.
     * <ul>
     * <li>Activa, o que ya estuvo activa (tiene plantillas): no se puede. {@link IllegalStateException}
     * "Esta temporada tiene historial de plantillas y no se puede borrar".</li>
     * <li>Futura con borrador del asistente: hace falta {@code confirmarBorrador}; si no,
     * {@link com.mikedev.mutxamelcf.model.BorradorPlantillasException}. Con confirmación, el borrador se borra en la
     * misma transacción, antes que la temporada.</li>
     * <li>Sin plantillas: se borra.</li>
     * </ul>
     * Un error de integridad con otros datos (cuotas, conceptos...) se convierte en un {@link IllegalStateException}
     * con mensaje claro.
     */
    void eliminar(Long id, boolean confirmarBorrador);

    /** Cuántos jugadores tiene la temporada en JUGADOR_TEMPORADA (historial o borrador). */
    int jugadoresConBorrador(Long id);
}