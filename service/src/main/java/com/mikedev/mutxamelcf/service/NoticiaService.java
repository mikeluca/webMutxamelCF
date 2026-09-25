package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.NoticiaAppDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;

public interface NoticiaService {

	boolean guardarNoticia(NoticiaDTO noticia);

	List<NoticiaDTO> obtenerNoticiasParaMostrar();

	List<NoticiaDTO> obtenerTodas();

	NoticiaDTO obtenerNoticiaPorId(int id);

	void eliminarNoticia(int id);

	/**
	 * Página de noticias para la app, de más reciente a más antigua.
	 * Sin antesId, las "limite" (por defecto 5) más recientes; con
	 * antesId, las inmediatamente anteriores a esa noticia. limite se
	 * acota siempre entre 1 y 20.
	 */
	List<NoticiaAppDTO> obtenerNoticiasParaAppPagina(Integer antesId, Integer limite);

	NoticiaAppDTO obtenerNoticiaParaApp(int id);

	byte[] obtenerImagenNoticia(int id);

	byte[] obtenerImagenNoticiaMini(int id);

}
