package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;
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

	/**
	 * Cancela un partido (CANCELADO = 1) sin borrarlo, con el mismo
	 * control de permisos que el resto de operaciones "App".
	 */
	void cancelar(Long usuarioAppId, Long partidoId);

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

	/**
	 * Partidos de un equipo cuya fecha cae dentro del rango indicado,
	 * usado por el endpoint combinado de calendario. Lectura pública
	 * dentro de /api/app (solo exige estar autenticado, sin comprobar
	 * puedeGestionarEquipo), igual que obtenerPorEquipo.
	 */
	List<PartidoDTO> obtenerPorEquipoYRangoFechas(Long equipoId, LocalDate desde, LocalDate hasta);

	/**
	 * Igual que {@link #obtenerPorEquipoYRangoFechas(Long, LocalDate, LocalDate)},
	 * pero además indica si el jugador dado está convocado a cada
	 * partido: {@code true}/{@code false} si el partido ya tiene una
	 * convocatoria creada, o {@code null} si todavía no la tiene (no se
	 * puede afirmar "no convocado" de un partido sin convocatoria).
	 */
	List<PartidoDTO> obtenerPorEquipoYRangoFechas(Long equipoId, LocalDate desde, LocalDate hasta, Long jugadorId);

	// Compatibilidad con el contrato público ya existente
	List<ResultadoDTO> obtenerResultados(String deporte);

	ResultadoDTO obtenerResultadoPrimerEquipo();

}
