package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;

import com.mikedev.mutxamelcf.model.AsignacionEquipoFfcv;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;

/**
 * Cuerpo del POST /admin/ffcv/confirmar: la previsualización que devolvió
 * el servidor más las relaciones nuevas que el usuario eligió para las
 * filas SIN_EQUIPO.
 */
public record ConfirmarFfcvRequest(
        PrevisualizacionFfcv previsualizacion,
        List<AsignacionEquipoFfcv> asignaciones) {
}
