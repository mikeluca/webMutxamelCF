package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipo;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.FusionFichasDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.util.ImageUtils;

@Service
public class CuerpoTecnicoServiceImpl implements CuerpoTecnicoService {

	private static final Logger logger = LoggerFactory.getLogger(CuerpoTecnicoServiceImpl.class);

	private final CuerpoTecnicoDao cuerpoTecnicoDao;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final EquipoDao equipoDao;

	public CuerpoTecnicoServiceImpl(CuerpoTecnicoDao cuerpoTecnicoDao, UsuarioAppVinculoDao usuarioAppVinculoDao,
			EquipoDao equipoDao) {
		this.cuerpoTecnicoDao = cuerpoTecnicoDao;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.equipoDao = equipoDao;
	}

	// ============================================================
	// Guardar
	// ============================================================

	@Override
	@Transactional
	public boolean guardarCuerpoTecnico(CuerpoTecnicoDTO dto) {
		logger.debug("Inicio guardarCuerpoTecnico: id={}", dto == null ? null : dto.getId());

		// Cada equipo una sola vez, con su puesto solo si difiere del general
		Map<Long, CuerpoTecnicoEquipo> deseados = new LinkedHashMap<>();
		Map<Long, Equipo> equipos = new LinkedHashMap<>();
		for (CuerpoTecnicoEquipoDTO e : dto.getEquipos()) {
			if (e.equipoId() == null || deseados.containsKey(e.equipoId())) {
				continue;
			}
			Equipo equipo = buscarEquipo(e.equipoId());
			if (equipo == null) {
				throw new IllegalArgumentException("El equipo seleccionado no existe.");
			}
			equipos.put(e.equipoId(), equipo);
			deseados.put(e.equipoId(), new CuerpoTecnicoEquipo(e.equipoId(), puestoDeEquipo(e.puesto(), dto.getPuesto())));
		}

		CuerpoTecnico entidad = toEntity(dto);
		aplicarCopiaInformativa(entidad, equipos.values());

		boolean resultado = cuerpoTecnicoDao.guardar(entidad);
		if (resultado) {
			cuerpoTecnicoDao.reemplazarEquipos(entidad.getId(), new ArrayList<>(deseados.values()));
		}

		logger.debug("Fin guardarCuerpoTecnico: resultado={}", resultado);
		return resultado;
	}

	/** El puesto de un equipo se guarda solo si es otro distinto del general; vacío o igual = NULL. */
	private static String puestoDeEquipo(String puestoEquipo, String puestoGeneral) {
		String propio = normalizar(puestoEquipo);
		if (propio.isEmpty() || propio.equalsIgnoreCase(normalizar(puestoGeneral))) {
			return null;
		}
		return puestoEquipo.trim();
	}

	private static String normalizar(String texto) {
		return texto == null ? "" : texto.trim();
	}

	/** La copia informativa de la ficha es el primer equipo por nombre (o nada si no tiene ninguno). */
	private static void aplicarCopiaInformativa(CuerpoTecnico ficha, Iterable<Equipo> equipos) {
		Equipo primero = null;
		for (Equipo e : equipos) {
			if (primero == null || String.valueOf(e.getNombre()).compareToIgnoreCase(String.valueOf(primero.getNombre())) < 0) {
				primero = e;
			}
		}
		ficha.setEquipo(primero == null ? null : primero.getNombre());
		ficha.setCategoria(primero == null ? null : primero.getCategoria());
		ficha.setDeporte(primero == null ? null : primero.getDeporte());
	}

	private Equipo buscarEquipo(Long equipoId) {
		try {
			return equipoDao.obtenerEquipoPorId(equipoId);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	// ============================================================
	// Lectura
	// ============================================================

	@Override
	public CuerpoTecnicoDTO obtenerCuerpoTecnicoPorId(Long id) {
		logger.debug("Inicio obtenerCuerpoTecnicoPorId: id={}", id);
		CuerpoTecnicoDTO staff = toDTO(cuerpoTecnicoDao.obtenerPorId(id));
		logger.debug("Fin obtenerCuerpoTecnicoPorId: id={}, encontrado={}", id, staff != null);
		return staff;
	}

	@Override
	@Transactional
	public void eliminarCuerpoTecnico(Long id) {
		logger.debug("Inicio eliminarCuerpoTecnico: id={}", id);

		if (usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(id)) {
			logger.warn("No se puede eliminar el cuerpo tecnico id={}: tiene cuenta de la app vinculada", id);
			throw new IllegalStateException(
					"No se puede eliminar porque tiene una cuenta de la app móvil vinculada. "
							+ "Desactiva o elimina antes esa cuenta desde 'Usuarios de la App'.");
		}

		cuerpoTecnicoDao.eliminar(id);
		logger.debug("Fin eliminarCuerpoTecnico: id={}", id);
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorCategoria(String categoria) {
		logger.debug("Inicio obtenerCuerpoTecnicoPorCategoria: categoria={}", categoria);
		List<CuerpoTecnicoDTO> lista = toDTOList(cuerpoTecnicoDao.obtenerTodosPorCategoria(categoria));
		logger.debug("Fin obtenerCuerpoTecnicoPorCategoria: categoria={}, total={}", categoria, lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorEquipo(String equipo) {
		logger.debug("Inicio obtenerCuerpoTecnicoPorEquipo: equipo={}", equipo);
		List<CuerpoTecnicoDTO> lista = toDTOList(cuerpoTecnicoDao.obtenerTodosPorEquipo(equipo));
		logger.debug("Fin obtenerCuerpoTecnicoPorEquipo: equipo={}, total={}", equipo, lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		List<CuerpoTecnicoDTO> lista = toDTOList(cuerpoTecnicoDao.obtenerTodos());
		logger.debug("Fin obtenerTodos: total={}", lista.size());
		return lista;
	}

	@Override
	public boolean equipoTieneCuerpoTecnico(Long equipoId) {
		return cuerpoTecnicoDao.existenParaEquipo(equipoId);
	}

	@Override
	public List<CuerpoTecnicoPublicDTO> obtenerCuerpoTecnicoPublicoPorEquipoId(Long equipoId) {
		logger.debug("Inicio obtenerCuerpoTecnicoPublicoPorEquipoId: equipoId={}", equipoId);

		List<CuerpoTecnicoPublicDTO> resultado = new ArrayList<>();

		for (CuerpoTecnico persona : cuerpoTecnicoDao.obtenerTodosPorEquipoId(equipoId)) {

			CuerpoTecnicoPublicDTO dto = new CuerpoTecnicoPublicDTO();

			dto.setId(persona.getId());
			dto.setNombre(persona.getNombre());
			dto.setApellidos(persona.getApellidos());
			dto.setCategoria(persona.getCategoria());
			dto.setEquipo(persona.getEquipo());
			dto.setDeporte(persona.getDeporte());
			// El puesto en ESTE equipo
			dto.setPuesto(persona.getPuesto());
			dto.setFotoBase64(ImageUtils.convertirAMiniaturaBase64(persona.getFoto()));

			resultado.add(dto);
		}

		logger.debug("Fin obtenerCuerpoTecnicoPublicoPorEquipoId: equipoId={}, total={}", equipoId, resultado.size());
		return resultado;
	}

	// ============================================================
	// Fusionar fichas
	// ============================================================

	/** Lo que se hará: la ficha conservada, las duplicadas y los equipos finales de la conservada. */
	private record Plan(CuerpoTecnico conservada, List<CuerpoTecnico> duplicadas,
			Map<Long, CuerpoTecnicoEquipo> equiposFinales, List<CuerpoTecnicoEquipo> nuevos,
			List<CuerpoTecnicoEquipo> yaPresentes) {
	}

	private Plan planificar(Long conservarId, List<Long> duplicadasIds) {
		if (conservarId == null) {
			throw new IllegalArgumentException("Hay que elegir la ficha que se conserva.");
		}
		Set<Long> ids = new LinkedHashSet<>(duplicadasIds == null ? List.of() : duplicadasIds);
		ids.remove(null);
		if (ids.contains(conservarId)) {
			throw new IllegalArgumentException("No se puede fusionar una ficha consigo misma.");
		}
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("Hay que elegir al menos una ficha duplicada.");
		}

		CuerpoTecnico conservada = cuerpoTecnicoDao.obtenerPorId(conservarId);
		if (conservada == null) {
			throw new IllegalArgumentException("La ficha que se conserva no existe.");
		}

		Map<Long, CuerpoTecnicoEquipo> finales = new LinkedHashMap<>();
		for (CuerpoTecnicoEquipo e : conservada.getEquipos()) {
			finales.put(e.getEquipoId(), copiaDe(e, e.getPuesto()));
		}

		List<CuerpoTecnico> duplicadas = new ArrayList<>();
		List<CuerpoTecnicoEquipo> nuevos = new ArrayList<>();
		List<CuerpoTecnicoEquipo> yaPresentes = new ArrayList<>();

		for (Long id : ids) {
			CuerpoTecnico duplicada = cuerpoTecnicoDao.obtenerPorId(id);
			if (duplicada == null) {
				throw new IllegalArgumentException("Una de las fichas duplicadas no existe.");
			}
			duplicadas.add(duplicada);

			for (CuerpoTecnicoEquipo e : duplicada.getEquipos()) {
				if (finales.containsKey(e.getEquipoId())) {
					// Si el equipo ya está, se mantiene el de la conservada
					yaPresentes.add(e);
					continue;
				}
				// Su puesto en ese equipo (el propio o el general de la duplicada) pasa a ser el puesto del equipo
				// solo si difiere del general de la conservada.
				String puesto = puestoDeEquipo(e.getPuestoMostrado(), conservada.getPuesto());
				CuerpoTecnicoEquipo nuevo = copiaDe(e, puesto);
				finales.put(e.getEquipoId(), nuevo);
				nuevos.add(nuevo);
			}
		}

		return new Plan(conservada, duplicadas, finales, nuevos, yaPresentes);
	}

	private static CuerpoTecnicoEquipo copiaDe(CuerpoTecnicoEquipo origen, String puesto) {
		CuerpoTecnicoEquipo copia = new CuerpoTecnicoEquipo(origen.getEquipoId(), puesto);
		copia.setEquipoNombre(origen.getEquipoNombre());
		copia.setCategoria(origen.getCategoria());
		copia.setDeporte(origen.getDeporte());
		return copia;
	}

	private static String nombreCompleto(CuerpoTecnico c) {
		return (normalizar(c.getNombre()) + " " + normalizar(c.getApellidos())).trim();
	}

	@Override
	public FusionFichasDTO resumenFusion(Long conservarId, List<Long> duplicadasIds) {
		Plan plan = planificar(conservarId, duplicadasIds);

		int vinculos = 0;
		for (CuerpoTecnico d : plan.duplicadas()) {
			vinculos += cuerpoTecnicoDao.contarVinculosApp(d.getId());
		}

		return new FusionFichasDTO(nombreCompleto(plan.conservada()),
				plan.duplicadas().stream().map(CuerpoTecnicoServiceImpl::nombreCompleto).toList(),
				plan.nuevos().stream().map(e -> e.getEquipoNombre()
						+ (e.getPuesto() == null ? "" : " (" + e.getPuesto() + ")")).toList(),
				plan.yaPresentes().stream().map(CuerpoTecnicoEquipo::getEquipoNombre).distinct().toList(),
				vinculos);
	}

	@Override
	@Transactional
	public void fusionarFichas(Long conservarId, List<Long> duplicadasIds) {
		logger.debug("Inicio fusionarFichas: conservar={}, duplicadas={}", conservarId, duplicadasIds);
		Plan plan = planificar(conservarId, duplicadasIds);

		cuerpoTecnicoDao.reemplazarEquipos(conservarId, new ArrayList<>(plan.equiposFinales().values()));

		for (CuerpoTecnico duplicada : plan.duplicadas()) {
			cuerpoTecnicoDao.moverVinculosApp(duplicada.getId(), conservarId);
			cuerpoTecnicoDao.eliminar(duplicada.getId());
		}

		// La copia informativa de la conservada sigue a sus equipos
		List<Equipo> equipos = new ArrayList<>();
		for (CuerpoTecnicoEquipo e : plan.equiposFinales().values()) {
			Equipo equipo = new Equipo();
			equipo.setNombre(e.getEquipoNombre());
			equipo.setCategoria(e.getCategoria());
			equipo.setDeporte(e.getDeporte());
			equipos.add(equipo);
		}
		CuerpoTecnico copia = new CuerpoTecnico();
		aplicarCopiaInformativa(copia, equipos);
		cuerpoTecnicoDao.actualizarCopiaInformativa(conservarId, copia.getEquipo(), copia.getCategoria(),
				copia.getDeporte());

		logger.info("Fichas fusionadas: conservada={}, duplicadasBorradas={}", conservarId, plan.duplicadas().size());
	}

	// ============================================================
	// Mapeos
	// ============================================================

	// Método para mapear CuerpoTecnicoDTO a CuerpoTecnico
	private static CuerpoTecnico toEntity(CuerpoTecnicoDTO cuerpoTecnicoDTO) {
		if (cuerpoTecnicoDTO == null) {
			return null;
		}

		CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
		cuerpoTecnico.setId(cuerpoTecnicoDTO.getId());
		cuerpoTecnico.setNombre(cuerpoTecnicoDTO.getNombre());
		cuerpoTecnico.setApellidos(cuerpoTecnicoDTO.getApellidos());
		cuerpoTecnico.setPuesto(cuerpoTecnicoDTO.getPuesto());
		cuerpoTecnico.setFoto(cuerpoTecnicoDTO.getFoto());

		return cuerpoTecnico;
	}

	// Método para mapear CuerpoTecnico a CuerpoTecnicoDTO
	private static CuerpoTecnicoDTO toDTO(CuerpoTecnico cuerpoTecnico) {
		if (cuerpoTecnico == null) {
			return null;
		}

		CuerpoTecnicoDTO cuerpoTecnicoDTO = new CuerpoTecnicoDTO();
		cuerpoTecnicoDTO.setId(cuerpoTecnico.getId());
		cuerpoTecnicoDTO.setNombre(cuerpoTecnico.getNombre());
		cuerpoTecnicoDTO.setApellidos(cuerpoTecnico.getApellidos());
		cuerpoTecnicoDTO.setCategoria(cuerpoTecnico.getCategoria());
		cuerpoTecnicoDTO.setDeporte(cuerpoTecnico.getDeporte());
		cuerpoTecnicoDTO.setEquipo(cuerpoTecnico.getEquipo());
		cuerpoTecnicoDTO.setPuesto(cuerpoTecnico.getPuesto());
		cuerpoTecnicoDTO.setFoto(cuerpoTecnico.getFoto());
		cuerpoTecnicoDTO.setEquipos(cuerpoTecnico.getEquipos().stream()
				.sorted(Comparator.comparing(CuerpoTecnicoEquipo::getEquipoNombre,
						Comparator.nullsLast(String::compareToIgnoreCase)))
				.map(e -> new CuerpoTecnicoEquipoDTO(e.getEquipoId(), e.getEquipoNombre(), e.getCategoria(),
						e.getPuesto(), e.getPuestoMostrado()))
				.toList());

		if (cuerpoTecnicoDTO.getFoto() != null && cuerpoTecnicoDTO.getFoto().length > 0) {
			String imagenBase64 = Base64.getEncoder().encodeToString(cuerpoTecnicoDTO.getFoto());
			cuerpoTecnicoDTO.setFotoBase64(imagenBase64);
		} else {
			cuerpoTecnicoDTO.setFotoBase64(null);
		}

		return cuerpoTecnicoDTO;
	}

	// Métodos para transformar listas de entidades a listas de DTOs
	private static List<CuerpoTecnicoDTO> toDTOList(List<CuerpoTecnico> staff) {
		List<CuerpoTecnicoDTO> listaStaff = new ArrayList<CuerpoTecnicoDTO>();
		for (CuerpoTecnico j : staff) {
			listaStaff.add(toDTO(j));
		}
		return listaStaff;
	}

}
