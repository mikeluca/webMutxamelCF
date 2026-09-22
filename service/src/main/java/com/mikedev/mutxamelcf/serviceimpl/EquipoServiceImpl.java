package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.model.EnumEquipos;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Service
public class EquipoServiceImpl implements EquipoService {

	private static final Logger logger = LoggerFactory.getLogger(EquipoServiceImpl.class);

	private final EquipoDao equipoDao;
	private final JugadorService jugadorService;
	private final CuerpoTecnicoService cuerpoTecnicoService;
	private final ConvocatoriaDao convocatoriaDao;
	private final EntrenamientoDao entrenamientoDao;

	public EquipoServiceImpl(EquipoDao equipoDao, JugadorService jugadorService,
			CuerpoTecnicoService cuerpoTecnicoService, ConvocatoriaDao convocatoriaDao,
			EntrenamientoDao entrenamientoDao) {
		this.equipoDao = equipoDao;
		this.jugadorService = jugadorService;
		this.cuerpoTecnicoService = cuerpoTecnicoService;
		this.convocatoriaDao = convocatoriaDao;
		this.entrenamientoDao = entrenamientoDao;
	}

	@Override
	public boolean guardar(EquipoDTO equipo) {
		logger.debug("Inicio guardar: categoria={}, grupo={}", equipo == null ? null : equipo.getCategoria(),
				equipo == null ? null : equipo.getGrupo());
		boolean resultado = equipoDao.guardar(toEntity(equipo));
		logger.debug("Fin guardar: resultado={}", resultado);
		return resultado;
	}

	@Override
	public EquipoDTO obtenerEquipoPorId(Long id) {
		logger.debug("Inicio obtenerEquipoPorId: id={}", id);
		EquipoDTO equipo = toDTO(equipoDao.obtenerEquipoPorId(id));
		logger.debug("Fin obtenerEquipoPorId: id={}", id);
		return equipo;
	}

	@Override
	public void eliminarEquipo(Long id) {
		logger.debug("Inicio eliminarEquipo: id={}", id);

		Equipo equipo = equipoDao.obtenerEquipoPorId(id);
		if (equipo == null) {
			logger.warn("Equipo inexistente al eliminar: id={}", id);
			return;
		}

		List<String> motivos = new ArrayList<>();

		if (!jugadorService.obtenerJugadoresPorEquipo(equipo.getNombre()).isEmpty()) {
			motivos.add("tiene jugadores asignados");
		}
		if (!cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo(equipo.getNombre()).isEmpty()) {
			motivos.add("tiene cuerpo técnico asignado");
		}
		if (!convocatoriaDao.obtenerPorEquipo(id).isEmpty()) {
			motivos.add("tiene convocatorias registradas");
		}
		if (!entrenamientoDao.obtenerPorEquipo(id).isEmpty()) {
			motivos.add("tiene entrenamientos registrados");
		}

		if (!motivos.isEmpty()) {
			logger.warn("No se puede eliminar el equipo id={}: {}", id, motivos);
			throw new IllegalStateException(
					"No se puede eliminar el equipo porque " + String.join(", ", motivos)
							+ ". Reasigna o elimina antes esos datos.");
		}

		equipoDao.eliminarEquipo(id);
		logger.debug("Fin eliminarEquipo: id={}", id);
	}

	@Override
	public List<String> obtenerCategorias() {
		logger.debug("Inicio obtenerCategorias");
		List<String> categorias = equipoDao.obtenerCategorias();
		logger.debug("Fin obtenerCategorias: total={}", categorias.size());
		return categorias;
	}

	@Override
	public List<EquipoDTO> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		List<EquipoDTO> equipos = toDTOList(equipoDao.obtenerTodos());
		equipos.sort(Comparator
				.comparing((EquipoDTO equipo) -> equipo.getOrden() == null ? "" : equipo.getOrden(),
						Comparator.nullsLast(String::compareTo))
				.thenComparing(EquipoDTO::getNombre, Comparator.nullsLast(String::compareTo)));
		logger.debug("Fin obtenerTodos: total={}", equipos.size());
		return equipos;
	}

	@Override
	public List<EquipoDTO> obtenerTodosPorCategoria(String categoria) {
		logger.debug("Inicio obtenerTodosPorCategoria: categoria={}", categoria);
		List<EquipoDTO> equipos = toDTOList(equipoDao.obtenerTodosPorCategoria(categoria));
		equipos.sort(Comparator
				.comparing((EquipoDTO equipo) -> equipo.getOrden() == null ? "" : equipo.getOrden(),
						Comparator.nullsLast(String::compareTo))
				.thenComparing(EquipoDTO::getNombre, Comparator.nullsLast(String::compareTo)));
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, equipos.size());
		return equipos;
	}

	@Override
	public Map<String, List<EquipoDTO>> obtenerEquiposAgrupadosPorCategoria(String deporte) {
		logger.debug("Inicio obtenerEquiposAgrupadosPorCategoria: deporte={}", deporte);
		Map<String, List<EquipoDTO>> agrupados = mapToEquipoDTO(equipoDao.obtenerEquiposAgrupadosPorCategoria(deporte));
		logger.debug("Fin obtenerEquiposAgrupadosPorCategoria: deporte={}, grupos={}", deporte, agrupados.size());
		return agrupados;
	}

	// Método para mapear EquipoDTO a Equipo
	private static Equipo toEntity(EquipoDTO equipoDTO) {
		if (equipoDTO == null) {
			return null;
		}

		Equipo equipo = new Equipo();
		equipo.setId(equipoDTO.getId());
		equipo.setCategoria(equipoDTO.getCategoria().trim());
		equipo.setGrupo(equipoDTO.getGrupo().trim());
		equipo.setOrden(EnumEquipos.getOrdenByCategoria(equipoDTO.getCategoria()));
		equipo.setNombre(equipoDTO.getNombre().trim());
		equipo.setDeporte(equipoDTO.getDeporte().trim());

		return equipo;
	}

	// Método para mapear Equipo a EquipoDTO
	private EquipoDTO toDTO(Equipo equipo) {
		if (equipo == null) {
			return null;
		}

		EquipoDTO equipoDTO = new EquipoDTO();
		equipoDTO.setId(equipo.getId());
		equipoDTO.setCategoria(equipo.getCategoria().trim());
		equipoDTO.setGrupo(equipo.getGrupo().trim());
		equipoDTO.setOrden(equipo.getOrden());
		equipoDTO.setNombre(equipo.getNombre().trim());
		equipoDTO.setDeporte(equipo.getDeporte().trim());

		return equipoDTO;
	}

	// Métodos para transformar listas de entidades a listas de DTOs
	private List<EquipoDTO> toDTOList(List<Equipo> equipos) {
		List<EquipoDTO> listaEquipos = new ArrayList<EquipoDTO>();
		for (Equipo e : equipos) {
			listaEquipos.add(toDTO(e));
		}
		return listaEquipos;
	}

	// Agrupa y ordena los equipos de cada categoria, convirtiendolos a DTO
	private Map<String, List<EquipoDTO>> mapToEquipoDTO(Map<String, List<Equipo>> equiposPorCategoria) {
		return equiposPorCategoria.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> entry.getValue().stream()
								.sorted(Comparator.comparing(Equipo::getOrden))
								.map(this::toDTO)
								.collect(Collectors.toList())));
	}

}
