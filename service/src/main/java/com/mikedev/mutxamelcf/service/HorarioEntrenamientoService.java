package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.HorarioEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoResponse;

public interface HorarioEntrenamientoService {

    /**
     * Crea un horario recurrente y genera de inmediato sus sesiones
     * hasta hoy + 2 meses.
     */
    HorarioEntrenamientoResponse crear(Long usuarioAppId, HorarioEntrenamientoCrearRequest request);

    /**
     * Actualiza un horario recurrente. Si el día de la semana cambia,
     * las sesiones futuras PROGRAMADA generadas a partir de él (que ya
     * no casan con el nuevo día) se cancelan y se regeneran para el
     * nuevo día. Si se desactiva (activo=false), sus sesiones futuras
     * PROGRAMADA se cancelan y no se genera ninguna nueva.
     */
    HorarioEntrenamientoResponse actualizar(Long usuarioAppId, Long horarioId,
            HorarioEntrenamientoActualizarRequest request);

    /**
     * Desactiva el horario (ACTIVO = 0, no se borra la fila) y cancela
     * sus sesiones futuras PROGRAMADA.
     */
    void eliminar(Long usuarioAppId, Long horarioId);

    List<HorarioEntrenamientoResponse> obtenerActivosPorEquipo(Long usuarioAppId, Long equipoId);

}
