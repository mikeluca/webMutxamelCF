package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.EquipoFfcv;

public interface EquipoFfcvDao {

    List<EquipoFfcv> listarTodos();

    EquipoFfcv obtenerPorId(Long id);

    /**
     * Relación para esa competición (ya normalizada) y letra
     * ({@code null} = sin letra), o {@code null} si no existe.
     */
    EquipoFfcv obtenerPorCompeticionYLetra(String competicion, String letra);

    EquipoFfcv crear(EquipoFfcv equipoFfcv);

    void actualizar(EquipoFfcv equipoFfcv);

    void eliminar(Long id);

}
