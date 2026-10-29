package com.mikedev.mutxamelcf.mvc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mikedev.mutxamelcf.service.HistorialTemporadaService;

/**
 * Historial de temporadas en el panel: plantillas por equipo, cuerpo técnico, clasificación final y estadísticas por
 * equipo, con un selector de temporada. Solo SUPER y ADMIN (ver SecurityConfig). Solo existe en el panel.
 */
@Controller
@RequestMapping("/admin/historial")
public class HistorialAdminController {

	private static final Logger logger = LoggerFactory.getLogger(HistorialAdminController.class);

	private final HistorialTemporadaService historialService;

	public HistorialAdminController(HistorialTemporadaService historialService) {
		this.historialService = historialService;
	}

	@GetMapping
	public String pagina(@RequestParam(name = "temporadaId", required = false) Long temporadaId,
			@RequestParam(name = "equipoId", required = false) Long equipoId, Model model) {
		logger.debug("Inicio pagina historial: temporadaId={}, equipoId={}", temporadaId, equipoId);
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);
		model.addAttribute("temporadas", historialService.temporadas());

		if (temporadaId != null) {
			try {
				model.addAttribute("historial", historialService.obtener(temporadaId, equipoId));
				model.addAttribute("equipoId", equipoId);
			} catch (IllegalArgumentException e) {
				logger.warn("No se puede mostrar el historial: {}", e.getMessage());
				model.addAttribute("error", e.getMessage());
			}
		}

		return "admin/historial";
	}
}
