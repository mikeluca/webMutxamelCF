package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnico;

public interface CuerpoTecnicoDao {

	boolean guardar(CuerpoTecnico cuerpoTecnico);

	CuerpoTecnico obtenerPorId(Long id);

	List<CuerpoTecnico> obtenerTodosPorCategoria(String categoria);

	List<CuerpoTecnico> obtenerTodosPorEquipo(String equipo);

	List<CuerpoTecnico> obtenerTodos();

	void eliminar(Long id);

}
