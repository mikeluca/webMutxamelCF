package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mikedev.mutxamelcf.util.ImageUtils;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.HistorialJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Service
public class JugadorServiceImpl implements JugadorService {

	private static final Logger logger = LoggerFactory.getLogger(JugadorServiceImpl.class);

	// ORA-02292: "... (ESQUEMA.NOMBRE_CONSTRAINT) violada ..."
	private static final Pattern CONSTRAINT = Pattern.compile("\\(([^()]+)\\)");

	private final JugadorDao jugadorDao;
	private final FamiliarJugadorService familiarJugadorService;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final CuotaJugadorService cuotaJugadorService;
	private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
	private final EntrenamientoAsistenciaDao entrenamientoAsistenciaDao;
	private final PartidoEstadisticaJugadorDao partidoEstadisticaJugadorDao;
	private final PartidoLiveEventoDao partidoLiveEventoDao;
	private final JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao;
	private final JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;
	private final EquipoDao equipoDao;
	private final TemporadaDao temporadaDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final Clock clock;

	public JugadorServiceImpl(JugadorDao jugadorDao, FamiliarJugadorService familiarJugadorService,
			UsuarioAppVinculoDao usuarioAppVinculoDao, CuotaJugadorService cuotaJugadorService,
			ConvocatoriaJugadorDao convocatoriaJugadorDao, EntrenamientoAsistenciaDao entrenamientoAsistenciaDao,
			PartidoEstadisticaJugadorDao partidoEstadisticaJugadorDao, PartidoLiveEventoDao partidoLiveEventoDao,
			JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao,
			JustificacionFaltaPartidoDao justificacionFaltaPartidoDao, EquipoDao equipoDao, TemporadaDao temporadaDao,
			JugadorTemporadaDao jugadorTemporadaDao, Clock clock) {
		this.equipoDao = equipoDao;
		this.temporadaDao = temporadaDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.clock = clock;
		this.jugadorDao = jugadorDao;
		this.familiarJugadorService = familiarJugadorService;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.cuotaJugadorService = cuotaJugadorService;
		this.convocatoriaJugadorDao = convocatoriaJugadorDao;
		this.entrenamientoAsistenciaDao = entrenamientoAsistenciaDao;
		this.partidoEstadisticaJugadorDao = partidoEstadisticaJugadorDao;
		this.partidoLiveEventoDao = partidoLiveEventoDao;
		this.justificacionFaltaEntrenamientoDao = justificacionFaltaEntrenamientoDao;
		this.justificacionFaltaPartidoDao = justificacionFaltaPartidoDao;
	}

	@Override
	@Transactional
	public boolean guardarJugador(JugadorDTO jugador) {
		logger.debug("Inicio guardarJugador: id={}", jugador == null ? null : jugador.getId());
		Jugador entidad = toEntity(jugador);

		if (entidad.getId() != null) {
			Jugador existente = jugadorDao.obtenerPorId(entidad.getId());
			if (existente != null && existente.estaDeBaja()) {
				// Un jugador de baja no tiene equipo: vuelve a la plantilla con "Reactivar", no al editarlo.
				entidad.setEquipoId(null);
				entidad.setEquipo(null);
				entidad.setCategoria(null);
				entidad.setDeporte(null);
			}
		}

		boolean resultado = jugadorDao.guardarJugador(entidad);

		if (resultado) {
			// Misma transacción: la fila de la temporada activa sigue a lo que hay en JUGADORES.
			sincronizarTemporadaActiva(jugadorDao.obtenerPorId(entidad.getId()), null);
		}
		logger.debug("Fin guardarJugador: resultado={}", resultado);
		return resultado;
	}

	@Override
	@Transactional
	public void darDeBaja(Long id) {
		logger.debug("Inicio darDeBaja: id={}", id);
		Jugador jugador = jugadorDao.obtenerPorId(id);
		if (jugador == null) {
			throw new IllegalArgumentException("El jugador no existe.");
		}
		if (jugador.estaDeBaja()) {
			throw new IllegalStateException("El jugador ya está de baja.");
		}

		// La fila de la temporada guarda el equipo en el que estaba antes de que EQUIPO_ID pase a NULL.
		sincronizarTemporadaActiva(jugador, Boolean.TRUE);
		jugadorDao.darDeBaja(id, LocalDate.now(clock));
		logger.info("Jugador dado de baja: id={}", id);
		logger.debug("Fin darDeBaja: id={}", id);
	}

	@Override
	@Transactional
	public void reactivar(Long id, Long equipoId) {
		logger.debug("Inicio reactivar: id={}, equipoId={}", id, equipoId);
		Jugador jugador = jugadorDao.obtenerPorId(id);
		if (jugador == null) {
			throw new IllegalArgumentException("El jugador no existe.");
		}
		if (!jugador.estaDeBaja()) {
			throw new IllegalStateException("El jugador no está de baja.");
		}
		Equipo equipo = equipoId == null ? null : buscarEquipo(equipoId);
		if (equipo == null) {
			throw new IllegalArgumentException("Hay que elegir un equipo para reactivar al jugador.");
		}

		jugadorDao.reactivar(id, equipo.getId(), equipo.getNombre(), equipo.getCategoria(), equipo.getDeporte());
		sincronizarTemporadaActiva(jugadorDao.obtenerPorId(id), Boolean.FALSE);
		logger.info("Jugador reactivado: id={}, equipoId={}", id, equipoId);
		logger.debug("Fin reactivar: id={}", id);
	}

	private Equipo buscarEquipo(Long equipoId) {
		try {
			return equipoDao.obtenerEquipoPorId(equipoId);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return null;
		}
	}

	/**
	 * Crea o actualiza la fila de la temporada activa del jugador. {@code baja} fuerza ese valor; con {@code null}
	 * se deduce de FECHA_BAJA. Si no hay temporada activa no hay nada que mantener.
	 */
	private void sincronizarTemporadaActiva(Jugador jugador, Boolean baja) {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		if (activa == null || jugador == null) {
			return;
		}

		boolean deBaja = baja != null ? baja : jugador.estaDeBaja();
		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugador.getId(), activa.getId());
		if (fila == null) {
			fila = new JugadorTemporada();
			fila.setJugadorId(jugador.getId());
			fila.setTemporadaId(activa.getId());
		}

		// Un jugador que ya está de baja no tiene equipo: la fila conserva el último que tuvo.
		if (jugador.getEquipoId() != null || !deBaja) {
			fila.setEquipoId(jugador.getEquipoId());
			fila.setEquipoNombre(jugador.getEquipo());
			fila.setCategoria(jugador.getCategoria());
		}
		fila.setDorsal(jugador.getDorsal());
		fila.setBaja(deBaja);
		fila.setPendienteRevision(false);

		jugadorTemporadaDao.guardar(fila);
	}

	@Override
	public List<HistorialJugadorDTO> obtenerHistorial(Long jugadorId) {
		List<HistorialJugadorDTO> historial = new ArrayList<>();
		for (JugadorTemporada fila : jugadorTemporadaDao.listarPorJugador(jugadorId)) {
			historial.add(new HistorialJugadorDTO(fila.getTemporadaNombre(), fila.getEquipoNombre(), fila.getCategoria(),
					fila.getDorsal(), fila.isBaja()));
		}
		return historial;
	}

	@Override
	public JugadorDTO obtenerJugadorPorId(Long id) {
		logger.debug("Inicio obtenerJugadorPorId: id={}", id);
		JugadorDTO jugador = toDTO(jugadorDao.obtenerPorId(id));
		logger.debug("Fin obtenerJugadorPorId: id={}, encontrado={}", id, jugador != null);
		return jugador;
	}

	@Override
	@Transactional
	public void eliminarJugador(Long id) {
		logger.debug("Inicio eliminarJugador: id={}", id);

		List<String> motivos = new ArrayList<>();

		if (!familiarJugadorService.obtenerFamiliaresDeJugador(id).isEmpty()) {
			motivos.add("tiene familiares vinculados");
		}
		if (usuarioAppVinculoDao.jugadorTieneCuenta(id)) {
			motivos.add("tiene una cuenta de la app móvil vinculada");
		}
		if (!cuotaJugadorService.obtenerPorJugador(id).isEmpty()) {
			motivos.add("tiene cuotas asignadas");
		}
		if (convocatoriaJugadorDao.existePorJugador(id)) {
			motivos.add("aparece en convocatorias");
		}
		if (entrenamientoAsistenciaDao.existePorJugador(id)) {
			motivos.add("tiene asistencias a entrenamientos registradas");
		}
		if (partidoEstadisticaJugadorDao.existePorJugador(id)) {
			motivos.add("tiene estadísticas de partidos registradas");
		}
		if (partidoLiveEventoDao.existePorJugador(id)) {
			motivos.add("tiene goles en retransmisiones");
		}

		if (!motivos.isEmpty()) {
			logger.warn("No se puede eliminar el jugador id={}: {}", id, motivos);
			throw new IllegalStateException(
					"No se puede eliminar el jugador porque " + String.join(", ", motivos)
							+ ". Quita antes esas relaciones (desasigna familiares, cuenta de app, cuotas...).");
		}

		// Las justificaciones no bloquean: se borran en esta misma transacción justo antes del jugador.
		int justificacionesEntrenamiento = justificacionFaltaEntrenamientoDao.eliminarPorJugador(id);
		int justificacionesPartido = justificacionFaltaPartidoDao.eliminarPorJugador(id);
		logger.info("Justificaciones borradas junto al jugador id={}: entrenamientos={}, partidos={}", id,
				justificacionesEntrenamiento, justificacionesPartido);

		try {
			jugadorDao.eliminar(id);
		} catch (DataIntegrityViolationException e) {
			logger.warn("No se puede eliminar el jugador id={} por una restricción de integridad: constraint={}", id,
					nombreConstraint(e), e);
			throw new IllegalStateException("No se puede eliminar el jugador porque tiene datos relacionados en el club "
					+ "(historial). Revisa sus relaciones.", e);
		}
		logger.debug("Fin eliminarJugador: id={}", id);
	}

	/** Nombre de la constraint violada, p. ej. "ORA-02292: restricción de integridad (ESQ.FK_X) violada - ..." → "ESQ.FK_X". */
	private static String nombreConstraint(DataIntegrityViolationException e) {
		String mensaje = e.getMostSpecificCause().getMessage();
		if (mensaje != null) {
			Matcher m = CONSTRAINT.matcher(mensaje);
			if (m.find()) {
				return m.group(1);
			}
		}
		return "desconocida (" + mensaje + ")";
	}

	@Override
	public List<JugadorDTO> obtenerJugadoresPorCategoria(String categoria) {
		logger.debug("Inicio obtenerJugadoresPorCategoria: categoria={}", categoria);
		List<JugadorDTO> jugadores = toDTOList(jugadorDao.obtenerTodosPorCategoria(categoria));
		logger.debug("Fin obtenerJugadoresPorCategoria: categoria={}, total={}", categoria, jugadores.size());
		return jugadores;
	}

	@Override
	public List<JugadorDTO> obtenerJugadoresPorEquipo(String equipo) {
		logger.debug("Inicio obtenerJugadoresPorEquipo: equipo={}", equipo);
		List<JugadorDTO> jugadores = toDTOList(jugadorDao.obtenerTodosPorNombreEquipo(equipo));
		logger.debug("Fin obtenerJugadoresPorEquipo: equipo={}, total={}", equipo, jugadores.size());
		return jugadores;
	}

	@Override
	public List<JugadorDTO> obtenerActivos() {
		return toDTOList(jugadorDao.obtenerActivos());
	}

	@Override
	public List<JugadorDTO> obtenerBajas() {
		return toDTOList(jugadorDao.obtenerBajas());
	}

	@Override
	public List<JugadorDTO> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		List<JugadorDTO> jugadores = toDTOList(jugadorDao.obtenerTodos());
		logger.debug("Fin obtenerTodos: total={}", jugadores.size());
		return jugadores;
	}

	@Override
	public List<JugadorPublicDTO> obtenerJugadoresPublicosPorEquipo(
			String equipo) {

		logger.debug(
				"Inicio obtenerJugadoresPublicosPorEquipo: equipo={}",
				equipo);

		List<Jugador> jugadores = jugadorDao.obtenerTodosPorNombreEquipo(equipo);

		List<JugadorPublicDTO> resultado = new ArrayList<>();

		for (Jugador jugador : jugadores) {

			JugadorPublicDTO dto = new JugadorPublicDTO();

			dto.setId(jugador.getId());
			dto.setNombre(jugador.getNombre());
			dto.setApellidos(jugador.getApellidos());
			dto.setCategoria(jugador.getCategoria());
			dto.setEquipo(jugador.getEquipo());
			dto.setDeporte(jugador.getDeporte());
			dto.setDorsal(jugador.getDorsal());
			dto.setPosicion(jugador.getPosicion());

			dto.setFotoBase64(
					ImageUtils.convertirAMiniaturaBase64(
							jugador.getFoto()));

			resultado.add(dto);
		}

		logger.debug(
				"Fin obtenerJugadoresPublicosPorEquipo: equipo={}, total={}",
				equipo,
				resultado.size());

		return resultado;
	}

	// Método para mapear JugadorDTO a Jugador
	private static Jugador toEntity(JugadorDTO jugadorDTO) {
		if (jugadorDTO == null) {
			return null;
		}

		Jugador jugador = new Jugador();
		jugador.setId(jugadorDTO.getId());
		jugador.setNombre(jugadorDTO.getNombre());
		jugador.setApellidos(jugadorDTO.getApellidos());
		jugador.setCategoria(jugadorDTO.getCategoria());
		jugador.setDeporte(jugadorDTO.getDeporte());
		jugador.setEquipo(jugadorDTO.getEquipo());
		jugador.setEquipoId(jugadorDTO.getEquipoId());
		jugador.setAnioNacimiento(jugadorDTO.getAnioNacimiento());
		jugador.setDorsal(jugadorDTO.getDorsal() == null ? null : jugadorDTO.getDorsal());
		jugador.setPosicion(jugadorDTO.getPosicion());
		jugador.setFoto(jugadorDTO.getFoto());

		return jugador;
	}

	// Método para mapear Jugador a JugadorDTO
	private static JugadorDTO toDTO(Jugador jugador) {
		if (jugador == null) {
			return null;
		}

		JugadorDTO jugadorDTO = new JugadorDTO();
		jugadorDTO.setId(jugador.getId());
		jugadorDTO.setNombre(jugador.getNombre());
		jugadorDTO.setApellidos(jugador.getApellidos());
		jugadorDTO.setCategoria(jugador.getCategoria());
		jugadorDTO.setDeporte(jugador.getDeporte());
		jugadorDTO.setEquipo(jugador.getEquipo());
		jugadorDTO.setEquipoId(jugador.getEquipoId());
		jugadorDTO.setAnioNacimiento(jugador.getAnioNacimiento());
		jugadorDTO.setFechaBaja(jugador.getFechaBaja());
		jugadorDTO.setDorsal(jugador.getDorsal());
		jugadorDTO.setPosicion(jugador.getPosicion());
		jugadorDTO.setFoto(jugador.getFoto());

		if (jugadorDTO.getFoto() != null && jugadorDTO.getFoto().length > 0) {
			String imagenBase64 = Base64.getEncoder().encodeToString(jugadorDTO.getFoto());
			jugadorDTO.setFotoBase64(imagenBase64);
		} else {
			jugadorDTO.setFotoBase64(null);
		}

		return jugadorDTO;
	}

	// Métodos para transformar listas de entidades a listas de DTOs
	private static List<JugadorDTO> toDTOList(List<Jugador> jugadores) {
		List<JugadorDTO> listaJugadores = new ArrayList<JugadorDTO>();
		for (Jugador j : jugadores) {
			listaJugadores.add(toDTO(j));
		}
		return listaJugadores;
	}

}
