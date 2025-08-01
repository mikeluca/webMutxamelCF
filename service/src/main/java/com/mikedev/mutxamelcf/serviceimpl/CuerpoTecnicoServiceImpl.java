package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;

@Service
public class CuerpoTecnicoServiceImpl implements CuerpoTecnicoService {

	@Autowired
	CuerpoTecnicoDao cuerpoTecnicoDao;

	@Override
	public boolean guardarCuerpoTecnico(CuerpoTecnicoDTO cuerpoTecnico) {
		return cuerpoTecnicoDao.guardar(toEntity(cuerpoTecnico));
	}

	@Override
	public CuerpoTecnicoDTO obtenerCuerpoTecnicoPorId(Long id) {
		return toDTO(cuerpoTecnicoDao.obtenerPorId(id));
	}

	@Override
	public void eliminarCuerpoTecnico(Long id) {
		cuerpoTecnicoDao.eliminar(id);
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorCategoria(String categoria) {
		return toDTOList(cuerpoTecnicoDao.obtenerTodosPorCategoria(categoria));
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorEquipo(String equipo) {
		return toDTOList(cuerpoTecnicoDao.obtenerTodosPorEquipo(equipo));
	}

	@Override
	public List<CuerpoTecnicoDTO> obtenerTodos() {
		return toDTOList(cuerpoTecnicoDao.obtenerTodos());
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
