package com.mikedev.mutxamelcf.serviceimpl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.ResultadoDao;
import com.mikedev.mutxamelcf.model.Resultado;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.ResultadoService;

@Service
public class ResultadoServiceImpl implements ResultadoService {

	private static final Logger logger = LoggerFactory.getLogger(ResultadoServiceImpl.class);

	private final ResultadoDao resultadoDao;

	public ResultadoServiceImpl(ResultadoDao resultadoDao) {
		this.resultadoDao = resultadoDao;
	}

	@Override
	public void actualizarResultado(ResultadoDTO resultado) {
		logger.debug("Inicio actualizarResultado: categoria={}, equipo={}",
				resultado == null ? null : resultado.getCategoria(), resultado == null ? null : resultado.getEquipo());
		resultadoDao.actualizarResultado(toEntity(resultado));
		logger.debug("Fin actualizarResultado");
	}

	@Override
	public List<ResultadoDTO> obtenerResultados(String deporte) {
		logger.debug("Inicio obtenerResultados: deporte={}", deporte);
		List<ResultadoDTO> resultados = toDTOList(resultadoDao.obtenerResultados(deporte));
		logger.debug("Fin obtenerResultados: deporte={}, total={}", deporte, resultados.size());
		return resultados;
	}

	@Override
	public ResultadoDTO obtenerResultadoPrimerEquipo() {
		logger.debug("Inicio obtenerResultadoPrimerEquipo");

		ResultadoDTO resultado = toDTO(
				resultadoDao.obtenerResultadoPrimerEquipo());

		logger.debug(
				"Fin obtenerResultadoPrimerEquipo: encontrado={}",
				resultado != null);

		return resultado;
	}

	// Método para mapear Resultado a ResultadoDTO
	private static ResultadoDTO toDTO(Resultado resultado) {
		if (resultado == null) {
			return null;
		}
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");

		ResultadoDTO resultadoDTO = new ResultadoDTO();
		resultadoDTO.setCategoria(resultado.getCategoria());
		resultadoDTO.setEquipo(resultado.getEquipo());
		resultadoDTO.setRival(resultado.getRival());
		resultadoDTO.setResultado(resultado.getResultado());
		if (resultado.getDia() != null) {
			resultadoDTO.setDia(resultado.getDia());
			resultadoDTO.setDiaFormateado(formatter.format(resultado.getDia()));
		} else {
			resultadoDTO.setDia(null);
			resultadoDTO.setDiaFormateado("");

		}
		resultadoDTO.setHora(resultado.getHora());
		resultadoDTO.setCampo(resultado.getCampo());

		return resultadoDTO;
	}

	// Método para mapear ResultadoDTO a Resultado
	private static Resultado toEntity(ResultadoDTO resultadoDTO) {
		if (resultadoDTO == null) {
			return null;
		}

		Resultado resultado = new Resultado();
		resultado.setCategoria(resultadoDTO.getCategoria());
		resultado.setEquipo(resultadoDTO.getEquipo());
		resultado.setRival(resultadoDTO.getRival());
		resultado.setResultado(resultadoDTO.getResultado());
		resultado.setDia(resultadoDTO.getDia() == null ? null : resultadoDTO.getDia());
		resultado.setHora(resultadoDTO.getHora());
		resultado.setCampo(resultadoDTO.getCampo());

		return resultado;
	}

	// Métodos para transformar listas de entidades a listas de DTOs
	private static List<ResultadoDTO> toDTOList(List<Resultado> resultados) {
		List<ResultadoDTO> listaResultados = new ArrayList<ResultadoDTO>();
		for (Resultado j : resultados) {
			listaResultados.add(toDTO(j));
		}
		return listaResultados;
	}

}
