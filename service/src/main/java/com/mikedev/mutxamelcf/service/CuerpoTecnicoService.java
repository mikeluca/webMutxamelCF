package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;

public interface CuerpoTecnicoService {

	boolean guardarCuerpoTecnico(CuerpoTecnicoDTO cuerpoTecnico);

	List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorCategoria(String categoria);

	List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorEquipo(String equipo);

	List<CuerpoTecnicoDTO> obtenerTodos();

	CuerpoTecnicoDTO obtenerCuerpoTecnicoPorId(Long id);

	void eliminarCuerpoTecnico(Long id);

}
