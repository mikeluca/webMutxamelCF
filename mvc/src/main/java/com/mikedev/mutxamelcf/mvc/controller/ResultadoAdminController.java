package com.mikedev.mutxamelcf.mvc.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.PartidoService;

@Controller
@RequestMapping("/admin")
public class ResultadoAdminController {

	private static final Logger logger = LoggerFactory.getLogger(ResultadoAdminController.class);

	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final String DEPORTE_FUTBOL_SALA = "FS";

	private final PartidoService partidoService;

	private final EquipoService equiposService;

	public ResultadoAdminController(PartidoService partidoService, EquipoService equiposService) {
		this.partidoService = partidoService;
		this.equiposService = equiposService;
	}

	// Método para listar el calendario/resultados: un acordeón por equipo,
	// con todos sus partidos (histórico + futuros).
	@GetMapping("/calendario-resultados")
	public String listarResultados(Model model) {
		logger.debug("Inicio listarResultados");

		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos();
		model.addAttribute("listaEquipos", listaEquipos);

		List<EquipoConPartidos> equiposFutbol = new ArrayList<>();
		List<EquipoConPartidos> equiposFutbolSala = new ArrayList<>();

		for (EquipoDTO equipo : listaEquipos) {

			List<PartidoDTO> partidos = partidoService.obtenerPorEquipo(equipo.getId());

			EquipoConPartidos equipoConPartidos = new EquipoConPartidos(equipo, partidos);

			if (DEPORTE_FUTBOL_SALA.equals(equipo.getDeporte())) {
				equiposFutbolSala.add(equipoConPartidos);
			} else {
				equiposFutbol.add(equipoConPartidos);
			}
		}

		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("equiposFutbol", equiposFutbol);
		model.addAttribute("equiposFutbolSala", equiposFutbolSala);

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarResultados: equiposFutbol={}, equiposFutbolSala={}", equiposFutbol.size(),
				equiposFutbolSala.size());

		return "admin/calendario-resultados";
	}

	// Método para crear un partido nuevo para un equipo.
	@PostMapping("/calendario-resultados/crear")
	public String crear(@RequestParam Long equipoId, @RequestParam String rival,
			@RequestParam(required = false) String dia, @RequestParam(required = false) String hora,
			@RequestParam(required = false) String campo, @RequestParam(required = false) String resultado) {

		logger.debug("Inicio crear: equipoId={}, rival={}", equipoId, rival);

		try {
			PartidoGuardarRequest request = construirRequest(equipoId, rival, dia, hora, campo, resultado);
			partidoService.crearComoAdmin(request);
		} catch (FechaInvalidaException e) {
			logger.error("Error al parsear la fecha del partido: {}", e.getMessage(), e);
			return "redirect:/admin/calendario-resultados?error=Fecha inválida";
		} catch (Exception e) {
			logger.error("Error al crear el partido: {}", e.getMessage(), e);
			return "redirect:/admin/calendario-resultados?error=Error al crear el partido";
		}

		logger.debug("Fin crear: equipoId={}", equipoId);
		return "redirect:/admin/calendario-resultados";
	}

	// Método para actualizar un partido existente.
	@PostMapping("/calendario-resultados/{id}/actualizar")
	public String actualizar(@PathVariable Long id, @RequestParam Long equipoId, @RequestParam String rival,
			@RequestParam(required = false) String dia, @RequestParam(required = false) String hora,
			@RequestParam(required = false) String campo, @RequestParam(required = false) String resultado) {

		logger.debug("Inicio actualizar: id={}, equipoId={}", id, equipoId);

		try {
			PartidoGuardarRequest request = construirRequest(equipoId, rival, dia, hora, campo, resultado);
			partidoService.actualizarComoAdmin(id, request);
		} catch (FechaInvalidaException e) {
			logger.error("Error al parsear la fecha del partido: {}", e.getMessage(), e);
			return "redirect:/admin/calendario-resultados?error=Fecha inválida";
		} catch (Exception e) {
			logger.error("Error al actualizar el partido: {}", e.getMessage(), e);
			return "redirect:/admin/calendario-resultados?error=Error al actualizar el partido";
		}

		logger.debug("Fin actualizar: id={}", id);
		return "redirect:/admin/calendario-resultados";
	}

	// Método para eliminar un partido existente.
	@PostMapping("/calendario-resultados/{id}/eliminar")
	public String eliminar(@PathVariable Long id) {

		logger.debug("Inicio eliminar: id={}", id);

		try {
			partidoService.eliminarComoAdmin(id);
		} catch (Exception e) {
			logger.error("Error al eliminar el partido: {}", e.getMessage(), e);
			return "redirect:/admin/calendario-resultados?error=Error al eliminar el partido";
		}

		logger.debug("Fin eliminar: id={}", id);
		return "redirect:/admin/calendario-resultados";
	}

	private PartidoGuardarRequest construirRequest(Long equipoId, String rival, String dia, String hora,
			String campo, String resultado) {

		LocalDate fecha = null;

		if (dia != null && !dia.isBlank()) {
			try {
				fecha = LocalDate.parse(dia, FORMATO_FECHA);
			} catch (DateTimeParseException e) {
				throw new FechaInvalidaException(e);
			}
		}

		return new PartidoGuardarRequest(equipoId, rival, fecha, hora, campo, resultado);
	}

	private static final class FechaInvalidaException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		FechaInvalidaException(Throwable causa) {
			super("Fecha inválida", causa);
		}
	}
}
