package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.util.ImageUtils;

@Service
public class CuerpoTecnicoServiceImpl implements CuerpoTecnicoService {

	private static final Logger logger = LoggerFactory.getLogger(CuerpoTecnicoServiceImpl.class);

	private final CuerpoTecnicoDao cuerpoTecnicoDao;

	public CuerpoTecnicoServiceImpl(CuerpoTecnicoDao cuerpoTecnicoDao) {
		this.cuerpoTecnicoDao = cuerpoTecnicoDao;
	}

	@Override
	public boolean guardarCuerpoTecnico(CuerpoTecnicoDTO cuerpoTecnico) {
		logger.debug("Inicio guardarCuerpoTecnico: id={}", cuerpoTecnico == null ? null : cuerpoTecnico.getId());
		boolean resultado = cuerpoTecnicoDao.guardar(toEntity(cuerpoTecnico));
		logger.debug("Fin guardarCuerpoTecnico: resultado={}", resultado);
		return resultado;
	}

	@Override
	public CuerpoTecnicoDTO obtenerCuerpoTecnicoPorId(Long id) {
		logger.debug("Inicio obtenerCuerpoTecnicoPorId: id={}", id);
		CuerpoTecnicoDTO staff = toDTO(cuerpoTecnicoDao.obtenerPorId(id));
		logger.debug("Fin obtenerCuerpoTecnicoPorId: id={}, encontrado={}", id, staff != null);
		return staff;
	}

	@Override
	public void eliminarCuerpoTecnico(Long id) {
		logger.debug("Inicio eliminarCuerpoTecnico: id={}", id);
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
	public List<CuerpoTecnicoPublicDTO> obtenerCuerpoTecnicoPublicoPorEquipo(
			String equipo) {

		logger.debug(
				"Inicio obtenerCuerpoTecnicoPublicoPorEquipo: equipo={}",
				equipo);

		List<CuerpoTecnico> staff = cuerpoTecnicoDao.obtenerTodosPorEquipo(equipo);

		List<CuerpoTecnicoPublicDTO> resultado = new ArrayList<>();

		for (CuerpoTecnico persona : staff) {

			CuerpoTecnicoPublicDTO dto = new CuerpoTecnicoPublicDTO();

			dto.setId(persona.getId());
			dto.setNombre(persona.getNombre());
			dto.setApellidos(persona.getApellidos());
			dto.setCategoria(persona.getCategoria());
			dto.setEquipo(persona.getEquipo());
			dto.setDeporte(persona.getDeporte());
			dto.setPuesto(persona.getPuesto());

			dto.setFotoBase64(
					ImageUtils.convertirAMiniaturaBase64(
							persona.getFoto()));

			resultado.add(dto);
		}

		logger.debug(
				"Fin obtenerCuerpoTecnicoPublicoPorEquipo: equipo={}, total={}",
				equipo,
				resultado.size());

		return resultado;
	}

	// Método para mapear CuerpoTecnicoDTO a CuerpoTecnico
	private static CuerpoTecnico toEntity(CuerpoTecnicoDTO cuerpoTecnicoDTO) {
		if (cuerpoTecnicoDTO == null) {
			return null;
		}

		CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
		cuerpoTecnico.setId(cuerpoTecnicoDTO.getId());
		cuerpoTecnico.setNombre(cuerpoTecnicoDTO.getNombre());
		cuerpoTecnico.setApellidos(cuerpoTecnicoDTO.getApellidos());
		cuerpoTecnico.setCategoria(cuerpoTecnicoDTO.getCategoria());
		cuerpoTecnico.setDeporte(cuerpoTecnicoDTO.getDeporte());
		cuerpoTecnico.setEquipo(cuerpoTecnicoDTO.getEquipo());
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
