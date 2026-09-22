package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.mikedev.mutxamelcf.util.ImageUtils;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Service
public class JugadorServiceImpl implements JugadorService {

	private static final Logger logger = LoggerFactory.getLogger(JugadorServiceImpl.class);

	private final JugadorDao jugadorDao;
	private final FamiliarJugadorService familiarJugadorService;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final CuotaJugadorService cuotaJugadorService;
	private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
	private final EntrenamientoAsistenciaDao entrenamientoAsistenciaDao;

	public JugadorServiceImpl(JugadorDao jugadorDao, FamiliarJugadorService familiarJugadorService,
			UsuarioAppVinculoDao usuarioAppVinculoDao, CuotaJugadorService cuotaJugadorService,
			ConvocatoriaJugadorDao convocatoriaJugadorDao, EntrenamientoAsistenciaDao entrenamientoAsistenciaDao) {
		this.jugadorDao = jugadorDao;
		this.familiarJugadorService = familiarJugadorService;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.cuotaJugadorService = cuotaJugadorService;
		this.convocatoriaJugadorDao = convocatoriaJugadorDao;
		this.entrenamientoAsistenciaDao = entrenamientoAsistenciaDao;
	}

	@Override
	public boolean guardarJugador(JugadorDTO jugador) {
		logger.debug("Inicio guardarJugador: id={}", jugador == null ? null : jugador.getId());
		boolean resultado = jugadorDao.guardarJugador(toEntity(jugador));
		logger.debug("Fin guardarJugador: resultado={}", resultado);
		return resultado;
	}

	@Override
	public JugadorDTO obtenerJugadorPorId(Long id) {
		logger.debug("Inicio obtenerJugadorPorId: id={}", id);
		JugadorDTO jugador = toDTO(jugadorDao.obtenerPorId(id));
		logger.debug("Fin obtenerJugadorPorId: id={}, encontrado={}", id, jugador != null);
		return jugador;
	}

	@Override
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

		if (!motivos.isEmpty()) {
			logger.warn("No se puede eliminar el jugador id={}: {}", id, motivos);
			throw new IllegalStateException(
					"No se puede eliminar el jugador porque " + String.join(", ", motivos)
							+ ". Quita antes esas relaciones (desasigna familiares, cuenta de app, cuotas...).");
		}

		jugadorDao.eliminar(id);
		logger.debug("Fin eliminarJugador: id={}", id);
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
		List<JugadorDTO> jugadores = toDTOList(jugadorDao.obtenerTodosPorEquipo(equipo));
		logger.debug("Fin obtenerJugadoresPorEquipo: equipo={}, total={}", equipo, jugadores.size());
		return jugadores;
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

		List<Jugador> jugadores = jugadorDao.obtenerTodosPorEquipo(equipo);

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
