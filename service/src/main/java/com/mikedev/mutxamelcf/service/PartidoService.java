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

	void eliminar(Long usuarioAppId, Long partidoId);

	void eliminarComoAdmin(Long partidoId);

	// Lectura pública (sin auth)
	List<PartidoDTO> obtenerUltimosPorEquipo(Long equipoId, int limite);

	List<PartidoDTO> obtenerUltimosPorEquipoNombre(String equipoNombre, int limite);

	List<PartidoDTO> obtenerPorEquipo(Long equipoId);

	/**
	 * Partidos de un equipo que el entrenador puede usar para crear una
	 * convocatoria (los que todavía no tienen convocatoria asociada),
	 * comprobando permisos igual que el resto de operaciones "App".
	 *
	 * @param incluirPartidoId id de un partido a incluir en el listado
	 *                         aunque ya tenga convocatoria (para que,
	 *                         editando una convocatoria existente, su
	 *                         partido vinculado siga siendo
	 *                         seleccionable); puede ser {@code null}.
	 */
	List<PartidoDTO> obtenerPartidosSinConvocatoria(Long usuarioAppId, Long equipoId, Long incluirPartidoId);

	// Compatibilidad con el contrato público ya existente
	List<ResultadoDTO> obtenerResultados(String deporte);

	ResultadoDTO obtenerResultadoPrimerEquipo();

}
