package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.PartidoDTO;

/**
 * Agrupa un equipo con su lista de partidos (histórico completo, más
 * recientes primero) para pintar el acordeón del calendario/resultados
 * en el panel de administración.
 */
public class EquipoConPartidos {

	private final EquipoDTO equipo;
	private final List<PartidoDTO> partidos;

	public EquipoConPartidos(EquipoDTO equipo, List<PartidoDTO> partidos) {
		this.equipo = equipo;
		this.partidos = partidos;
	}

	public EquipoDTO getEquipo() {
		return equipo;
	}

	public List<PartidoDTO> getPartidos() {
		return partidos;
	}

}
