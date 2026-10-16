package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.DescansoEquipo;

public interface DescansoEquipoDao {

    /** Descansos cuya fecha cae en el rango (ambos extremos incluidos). */
    List<DescansoEquipo> obtenerPorRangoFechas(LocalDate desde, LocalDate hasta);

    /** Descansos de un equipo con fecha igual o posterior a {@code desde}. */
    List<DescansoEquipo> obtenerDesde(Long equipoId, LocalDate desde);

    void crear(DescansoEquipo descanso);

    void eliminar(Long id);

}
