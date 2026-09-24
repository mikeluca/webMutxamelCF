package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.model.ResultadoDTO;

public interface PartidoService {

	// App (JWT): con control de permisos vía EquipoGestionDao
	PartidoDTO crear(Long usuarioAppId, PartidoGuardarRequest request);

	PartidoDTO actualizar(Long usuarioAppId, Long partidoId, PartidoGuardarRequest request);

	// Admin web: ya autenticado por Spring Security en /admin/**
	PartidoDTO crearComoAdmin(PartidoGuardarRequest request);

	PartidoDTO actualizarComoAdmin(Long partidoId, PartidoGuardarRequest request);

	// Lectura pública (sin auth)
	List<PartidoDTO> obtenerUltimosPorEquipo(Long equipoId, int limite);

	List<PartidoDTO> obtenerUltimosPorEquipoNombre(String equipoNombre, int limite);

	List<PartidoDTO> obtenerPorEquipo(Long equipoId);

	// Compatibilidad con el contrato público ya existente
	List<ResultadoDTO> obtenerResultados(String deporte);

	ResultadoDTO obtenerResultadoPrimerEquipo();

}
