package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.JugadorService;

@Service
public class JugadorServiceImpl implements JugadorService {

	@Autowired
	JugadorDao jugadorDao;

	@Override
	public boolean guardarJugador(JugadorDTO jugador) {
		return jugadorDao.guardarJugador(toEntity(jugador));
	}

	@Override
	public JugadorDTO obtenerJugadorPorId(Long id) {
		return toDTO(jugadorDao.obtenerPorId(id));
	}

	@Override
	public void eliminarJugador(Long id) {
		jugadorDao.eliminar(id);
	}

	@Override
	public List<JugadorDTO> obtenerJugadoresPorCategoria(String categoria) {
		return toDTOList(jugadorDao.obtenerTodosPorCategoria(categoria));
	}

	@Override
	public List<JugadorDTO> obtenerJugadoresPorEquipo(String equipo) {
		return toDTOList(jugadorDao.obtenerTodosPorEquipo(equipo));
	}

	@Override
	public List<JugadorDTO> obtenerTodos() {
		return toDTOList(jugadorDao.obtenerTodos());
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
