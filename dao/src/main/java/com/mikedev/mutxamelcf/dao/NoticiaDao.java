package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Noticia;

public interface NoticiaDao {
	
	boolean guardarNoticia(Noticia noticia);

	List<Noticia> obtenerNoticiasParaMostrar();

	List<Noticia> obtenerTodas();

	Noticia obtenerNoticiaPorId(int id);

	void eliminarNoticia(int id);
	
}
