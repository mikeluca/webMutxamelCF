package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.SesionEntrenamiento;

public interface SesionEntrenamientoDao {

    SesionEntrenamiento crear(SesionEntrenamiento sesion);

    void actualizar(SesionEntrenamiento sesion);

    void cancelar(Long id, String motivo);

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
     * Elimina todas las sesiones futuras (FECHA >= desde) que todavía
     * están PROGRAMADA para ese horario: al desactivarse o cambiar de
     * día el horario semanal, esas sesiones nunca han sido "canceladas"
     * (no ha ocurrido nada que cancelar), simplemente dejan de existir.
     * "CANCELADA" queda reservado para la cancelación puntual de una
     * sesión concreta (con motivo), no para este caso. No toca sesiones
     * pasadas ni ya canceladas individualmente, para conservar el
     * histórico.
     */
    void eliminarFuturasProgramadasPorHorario(Long horarioId, LocalDate desde);

    /**
     * Las mismas sesiones que afectará {@link #eliminarFuturasProgramadasPorHorario},
     * consultadas ANTES de eliminarlas: se usa para saber qué ENTRENAMIENTOS
     * vinculados hay que eliminar primero (por la FK) al eliminar en bloque
     * las sesiones futuras de un horario (desactivación o cambio de día).
     */
    List<SesionEntrenamiento> obtenerFuturasProgramadasPorHorario(Long horarioId, LocalDate desde);

}
