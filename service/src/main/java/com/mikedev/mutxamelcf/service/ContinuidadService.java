package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.ContinuidadJugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.ResultadoPreguntaContinuidadDTO;
import com.mikedev.mutxamelcf.model.Temporada;

/**
 * Confirmación de continuidad con las familias (docs/transicion-temporada.md, §5 pasos 2 y 3): preguntar si cada
 * jugador formativo sigue la temporada siguiente, recordarlo cada semana y recoger la respuesta desde la app.
 */
public interface ContinuidadService {

	/** La temporada siguiente a la activa (la primera que empieza después), o null si no existe. */
	Temporada temporadaSiguiente();

	/**
	 * Genera el borrador de la temporada (sin tocar las filas que ya existen) y pregunta a las familias de cada
	 * jugador formativo pendiente que todavía no haya sido preguntado. Idempotente: repetirlo no vuelve a avisar.
	 */
	ResultadoPreguntaContinuidadDTO pedirConfirmacion(Long destinoId);

	/** Lo que hace el job del 1 de mayo: pedir la confirmación si existe la temporada siguiente. Null si no existe. */
	ResultadoPreguntaContinuidadDTO pedirConfirmacionSiHaySiguiente();

	/**
	 * Recordatorio semanal: un aviso a cada cuenta con jugadores preguntados que siguen en CONTINUIDAD = PENDIENTE.
	 * No hace nada una vez empezada la temporada siguiente.
	 *
	 * @return cuántas cuentas se han avisado
	 */
	int recordarPendientes();

	/** Los jugadores vinculados al usuario que tienen que confirmar, vacío si no hay borrador. */
	List<ContinuidadJugadorDTO> listar(Long usuarioAppId);

	/**
	 * Guarda la respuesta de la familia (SIGUE, NO_SIGUE o DUDA) con las mismas reglas que el botón del panel y avisa
	 * a los coordinadores si es NO_SIGUE o DUDA.
	 *
	 * @throws SecurityException        si el usuario no está vinculado con el jugador
	 * @throws IllegalArgumentException si la respuesta no es válida o el jugador no tiene que confirmar
	 * @throws IllegalStateException    si no hay temporada siguiente
	 */
	ContinuidadJugadorDTO responder(Long usuarioAppId, Long jugadorId, String respuesta);

	/**
	 * Alta de un jugador de prueba en la temporada siguiente, con las mismas reglas que el asistente.
	 *
	 * @throws SecurityException        si el usuario no es coordinador
	 * @throws IllegalArgumentException si los datos no son válidos
	 * @throws IllegalStateException    si no hay temporada siguiente
	 */
	JugadorTemporada altaPrueba(Long usuarioAppId, String nombre, String apellidos, Integer anioNacimiento);
}
