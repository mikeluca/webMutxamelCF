package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.EquipoFfcvDTO;
import com.mikedev.mutxamelcf.service.EquipoFfcvService;

@Service
public class EquipoFfcvServiceImpl implements EquipoFfcvService {

	private static final Logger logger = LoggerFactory.getLogger(EquipoFfcvServiceImpl.class);

	private static final Pattern LETRA_VALIDA = Pattern.compile("^[A-Z]{1,2}$");

	private static final int LONGITUD_MAXIMA_COMPETICION = 200;

	private final EquipoFfcvDao equipoFfcvDao;
	private final EquipoDao equipoDao;

	public EquipoFfcvServiceImpl(EquipoFfcvDao equipoFfcvDao, EquipoDao equipoDao) {
		this.equipoFfcvDao = equipoFfcvDao;
		this.equipoDao = equipoDao;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EquipoFfcvDTO> listar() {

		List<EquipoFfcvDTO> resultado = new ArrayList<>();

		for (EquipoFfcv relacion : equipoFfcvDao.listarTodos()) {
			Equipo equipo = equipoDao.obtenerEquipoPorId(relacion.getEquipoId());
			resultado.add(aDTO(relacion, equipo));
		}

		return resultado;
	}

	@Override
	@Transactional
	public EquipoFfcvDTO crear(String competicion, String letra, Long equipoId) {
		logger.debug("Inicio crear: equipoId={}", equipoId);

		EquipoFfcv relacion = validar(null, competicion, letra, equipoId);

		equipoFfcvDao.crear(relacion);

		logger.debug("Fin crear: id={}", relacion.getId());

		return aDTO(relacion, equipoDao.obtenerEquipoPorId(equipoId));
	}

	@Override
	@Transactional
	public EquipoFfcvDTO actualizar(Long id, String competicion, String letra, Long equipoId) {
		logger.debug("Inicio actualizar: id={}", id);

		if (id == null || equipoFfcvDao.obtenerPorId(id) == null) {
			throw new IllegalArgumentException("La relación no existe");
		}

		EquipoFfcv relacion = validar(id, competicion, letra, equipoId);

		equipoFfcvDao.actualizar(relacion);

		logger.debug("Fin actualizar: id={}", id);

		return aDTO(relacion, equipoDao.obtenerEquipoPorId(equipoId));
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		logger.debug("Inicio eliminar: id={}", id);

		if (id == null || equipoFfcvDao.obtenerPorId(id) == null) {
			throw new IllegalArgumentException("La relación no existe");
		}

		equipoFfcvDao.eliminar(id);

		logger.debug("Fin eliminar: id={}", id);
	}

	private EquipoFfcv validar(Long id, String competicion, String letra, Long equipoId) {

		String competicionNormalizada = FfcvCsvParser.normalizarCompeticion(competicion);

		if (competicionNormalizada.isEmpty()) {
			throw new IllegalArgumentException("La competición es obligatoria");
		}

		if (competicionNormalizada.length() > LONGITUD_MAXIMA_COMPETICION) {
			throw new IllegalArgumentException("La competición es demasiado larga");
		}

		String letraNormalizada = ImportacionFfcvServiceImpl.normalizarLetra(letra);

		if (letraNormalizada != null && !LETRA_VALIDA.matcher(letraNormalizada).matches()) {
			throw new IllegalArgumentException("La letra debe ser una o dos letras (A-Z) o quedar vacía");
		}

		if (equipoId == null || equipoDao.obtenerEquipoPorId(equipoId) == null) {
			throw new IllegalArgumentException("El equipo no existe");
		}

		EquipoFfcv duplicada = equipoFfcvDao.obtenerPorCompeticionYLetra(competicionNormalizada, letraNormalizada);

		if (duplicada != null && !duplicada.getId().equals(id)) {
			throw new IllegalArgumentException("Ya existe una relación para esa competición y letra");
		}

		return new EquipoFfcv(id, equipoId, competicionNormalizada, letraNormalizada);
	}

	private static EquipoFfcvDTO aDTO(EquipoFfcv relacion, Equipo equipo) {
		return new EquipoFfcvDTO(
				relacion.getId(),
				relacion.getEquipoId(),
				equipo != null ? equipo.getNombre() : null,
				relacion.getCompeticion(),
				relacion.getLetra());
	}

}
