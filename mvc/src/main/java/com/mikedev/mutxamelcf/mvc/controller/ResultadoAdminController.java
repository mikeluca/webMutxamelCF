package com.mikedev.mutxamelcf.mvc.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.ResultadoService;

@Controller
@RequestMapping("/admin")
public class ResultadoAdminController {

	private static final Logger logger = LoggerFactory.getLogger(ResultadoAdminController.class);

	private final ResultadoService resultadoService;

	private final EquipoService equiposService;

	public ResultadoAdminController(ResultadoService resultadoService, EquipoService equiposService) {
		this.resultadoService = resultadoService;
		this.equiposService = equiposService;
	}

	// Método para listar los resultados del calendario
	@GetMapping("/calendario-resultados")
	public String listarResultados(Model model) {
		logger.debug("Inicio listarResultados");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);

		// Obtener todos los resultados desde la base de datos
		List<ResultadoDTO> resultadosFutbol = resultadoService.obtenerResultados("F");
		List<ResultadoDTO> resultadosFutbolSala = resultadoService.obtenerResultados("FS");
		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("resultadosFutbol", resultadosFutbol);
		model.addAttribute("resultadosFutbolSala", resultadosFutbolSala);

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarResultados: futbol={}, futbolSala={}", resultadosFutbol.size(),
				resultadosFutbolSala.size());
		return "admin/calendario-resultados"; // Retornar la vista para listar resultados
	}

	// Método para actualizar el resultado de un equipo
	@PostMapping("/calendario-resultados/actualizar/{equipo}")
	public String actualizarResultado(@PathVariable String equipo, @RequestParam String categoria,
			@RequestParam String resultado, @RequestParam String rival, @RequestParam String dia,
			@RequestParam String hora, @RequestParam String campo) {
		logger.debug("Inicio actualizarResultado: equipo={}, categoria={}, rival={}", equipo, categoria, rival);
		ResultadoDTO resultadoDTO = new ResultadoDTO();
		resultadoDTO.setCategoria(categoria);
		resultadoDTO.setEquipo(equipo);
		resultadoDTO.setResultado(resultado);
		resultadoDTO.setRival(rival);

		// Parsear la fecha desde el formato "dd/MM/yyyy"
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		try {
			if (dia != null && !dia.isEmpty()) {
				Date date = formatter.parse(dia);
				resultadoDTO.setDia(date);
			} else {
				resultadoDTO.setDia(null);
			}

		} catch (ParseException e) {
			logger.error("Error al parsear la fecha del resultado: {}", e.getMessage(), e); // Registrar error de parseo
			return "redirect:/admin/calendario-resultados?error=Fecha inválida"; // Redirigir con mensaje de error
		}

		resultadoDTO.setHora(hora);
		resultadoDTO.setCampo(campo);

		try {
			resultadoService.actualizarResultado(resultadoDTO); // Actualizar el resultado en la base de datos
		} catch (Exception e) {
			logger.error("Error al actualizar el resultado: {}", e.getMessage(), e); // Registrar el error
			return "redirect:/admin/calendario-resultados?error=Error al actualizar el resultado"; // Redirigir con
																									// mensaje de error
		}

		logger.debug("Fin actualizarResultado: equipo={}", equipo);
		return "redirect:/admin/calendario-resultados"; // Redirigir a la lista de resultados
	}
}
