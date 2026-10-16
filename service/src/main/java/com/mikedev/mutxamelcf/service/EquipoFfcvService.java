package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.EquipoFfcvDTO;

/**
 * Mantenimiento de las relaciones competición FFCV + letra -> equipo del
 * club (para el cambio de temporada).
 */
public interface EquipoFfcvService {

    List<EquipoFfcvDTO> listar();

    EquipoFfcvDTO crear(String competicion, String letra, Long equipoId);

    EquipoFfcvDTO actualizar(Long id, String competicion, String letra, Long equipoId);

    void eliminar(Long id);

}
