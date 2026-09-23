package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.Convocatoria;

public interface ConvocatoriaDao {

        Convocatoria guardar(
                        Convocatoria convocatoria);

        Convocatoria obtenerPorId(
                        Long id);

        List<Convocatoria> obtenerPorEquipo(
                        Long equipoId);

        void eliminar(
                        Long id);

        void actualizar(Convocatoria convocatoria);

        boolean existePorEquipoYFecha(
                        Long equipoId,
                        LocalDate fechaPartido);
}