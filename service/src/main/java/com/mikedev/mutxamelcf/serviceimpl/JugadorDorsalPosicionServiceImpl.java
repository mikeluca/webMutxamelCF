package com.mikedev.mutxamelcf.serviceimpl;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;
import com.mikedev.mutxamelcf.model.PosicionJugador;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.JugadorDorsalPosicionService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Service
public class JugadorDorsalPosicionServiceImpl implements JugadorDorsalPosicionService {

	private static final Logger logger = LoggerFactory.getLogger(JugadorDorsalPosicionServiceImpl.class);

	static final int DORSAL_MINIMO = 1;
	static final int DORSAL_MAXIMO = 99;

	private final JugadorDao jugadorDao;
	private final EquipoGestionDao equipoGestionDao;
	private final TemporadaDao temporadaDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final JugadorService jugadorService;

	public JugadorDorsalPosicionServiceImpl(JugadorDao jugadorDao, EquipoGestionDao equipoGestionDao,
			TemporadaDao temporadaDao, JugadorTemporadaDao jugadorTemporadaDao, JugadorService jugadorService) {
		this.jugadorDao = jugadorDao;
		this.equipoGestionDao = equipoGestionDao;
		this.temporadaDao = temporadaDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.jugadorService = jugadorService;
	}

	@Override
	@Transactional
	public JugadorEquipoAppDTO actualizar(Long usuarioAppId, Long jugadorId, Integer dorsal, String posicionCodigo) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		Jugador jugador = jugadorId == null ? null : jugadorDao.obtenerPorId(jugadorId);

		if (jugador == null || jugador.estaDeBaja()) {
			throw new NoSuchElementException("El jugador no existe");
		}

		// El permiso se comprueba antes que nada y sin dar detalles del jugador.
		Long equipoId = jugador.getEquipoId();

		if (equipoId == null) {
			if (equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
				throw new IllegalArgumentException("El jugador no está en ningún equipo");
			}

			throw new SecurityException("No tienes permiso para editar a este jugador");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
			throw new SecurityException("No tienes permiso para editar a este jugador");
		}

		if (dorsal != null && (dorsal < DORSAL_MINIMO || dorsal > DORSAL_MAXIMO)) {
			throw new IllegalArgumentException(
					"El dorsal debe estar entre " + DORSAL_MINIMO + " y " + DORSAL_MAXIMO);
		}

		PosicionJugador posicion = null;

		if (posicionCodigo != null) {
			posicion = PosicionJugador.desdeCodigo(posicionCodigo);

			if (posicion == null) {
				throw new IllegalArgumentException(
						"La posición debe ser PORTERO, DEFENSA, CENTROCAMPISTA, EXTREMO, DELANTERO o null");
			}
		}

		// Misma transacción: bloquea al equipo y comprueba que nadie más tiene el dorsal.
		ReglaDorsal.comprobar(jugadorDao, equipoId, jugadorId, dorsal);

		String posicionNueva = posicion == null ? null : posicion.texto();

		jugadorDao.actualizarDorsalPosicion(jugadorId, dorsal, posicionNueva);

		// La plantilla de la temporada no queda desfasada (mismo criterio que el panel: la fila sigue al jugador).
		Temporada activa = temporadaDao.obtenerTemporadaActiva();

		if (activa != null) {
			jugadorTemporadaDao.actualizarDorsal(jugadorId, activa.getId(), dorsal);
		}

		logger.info("Dorsal y posición editados desde la app: usuarioAppId={}, jugadorId={}, dorsal {} -> {}, posición {} -> {}",
				usuarioAppId, jugadorId, jugador.getDorsal(), dorsal, jugador.getPosicion(), posicionNueva);

		JugadorDTO actualizado = jugadorService.obtenerJugadorPorId(jugadorId);
		JugadorEquipoAppDTO respuesta = JugadorEquipoAppDTO.desde(actualizado);
		respuesta.setEditable(true);

		return respuesta;
	}
}
