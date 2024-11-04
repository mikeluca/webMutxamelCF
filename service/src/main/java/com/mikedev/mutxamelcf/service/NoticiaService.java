package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.NoticiaDTO;

public interface NoticiaService {
	
	boolean guardarNoticia(NoticiaDTO noticia);

	List<NoticiaDTO> obtenerNoticiasParaMostrar();

	List<NoticiaDTO> obtenerTodas();

	NoticiaDTO obtenerNoticiaPorId(int id);

	void eliminarNoticia(int id);
	
}
