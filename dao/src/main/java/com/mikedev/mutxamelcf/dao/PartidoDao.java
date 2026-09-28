package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Partido;

public interface PartidoDao {

    Partido crear(Partido partido);

    void actualizar(Partido partido);

    Partido obtenerPorId(Long id);

    List<Partido> obtenerPorEquipo(Long equipoId);

    List<Partido> obtenerUltimosPorEquipo(Long equipoId, int limite);

    /**
     * Partidos de un equipo que todavía no tienen una convocatoria
     * asociada (para el selector de "crear convocatoria a partir de un
     * partido"), ordenados por fecha ascendente (los próximos primero).
     *
     * @param equipoId          equipo cuyos partidos se listan.
     * @param incluirPartidoId  id de un partido a incluir en el listado
     *                          aunque ya tenga convocatoria (usado al
     *                          editar una convocatoria existente, para
     *                          que el partido ya vinculado siga siendo
     *                          seleccionable); puede ser {@code null}.
     */
    List<Partido> obtenerPartidosSinConvocatoria(Long equipoId, Long incluirPartidoId);

    Partido obtenerMasRelevantePorEquipoNombre(String equipoNombre, String categoria);

    Partido obtenerMasRelevantePorEquipo(Long equipoId);

    void eliminar(Long id);

}
