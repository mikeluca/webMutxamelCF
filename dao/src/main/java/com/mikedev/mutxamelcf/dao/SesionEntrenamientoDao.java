package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.SesionEntrenamiento;

public interface SesionEntrenamientoDao {

    SesionEntrenamiento crear(SesionEntrenamiento sesion);

    void actualizar(SesionEntrenamiento sesion);

    void cancelar(Long id);

    SesionEntrenamiento obtenerPorId(Long id);

    List<SesionEntrenamiento> obtenerPorEquipoYRango(Long equipoId, LocalDate desde, LocalDate hasta);

    boolean existePorHorarioYFecha(Long horarioId, LocalDate fecha);

    /**
     * Última fecha para la que ya existe una sesión generada a partir de
     * ese horario, o {@code null} si todavía no se ha generado ninguna.
     * Se usa como punto de partida de generarSesiones() para no volver a
     * recorrer fechas ya generadas.
     */
    LocalDate obtenerUltimaFechaGenerada(Long horarioId);

    /**
     * Cancela (ESTADO='CANCELADA') todas las sesiones futuras
     * (FECHA >= desde) que todavía están PROGRAMADA para ese horario.
     * No toca sesiones pasadas, para conservar el histórico.
     */
    void cancelarFuturasProgramadasPorHorario(Long horarioId, LocalDate desde);

}
