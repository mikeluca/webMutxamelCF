package com.mikedev.mutxamelcf.mvc.controller;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.model.MorosidadDTO;
import com.mikedev.mutxamelcf.service.MorosidadService;

/**
 * Morosidad: las cuotas sin pagar de temporadas anteriores. Mismos roles que el resto de Pagos (SecurityConfig:
 * /admin/pagos/**). Las familias no las ven.
 */
@Controller
@RequestMapping("/admin/pagos/morosidad")
public class MorosidadController {

	private static final Logger logger = LoggerFactory.getLogger(MorosidadController.class);

	static final String MENSAJE_ERROR_BASE_DE_DATOS =
			"No se ha podido completar la operación: error de base de datos. Revisa los registros del servidor.";

	private final MorosidadService morosidadService;

	public MorosidadController(MorosidadService morosidadService) {
		this.morosidadService = morosidadService;
	}

	@GetMapping
	public String pagina(@RequestParam(name = "temporadaId", required = false) Long temporadaId,
			@RequestParam(name = "cobradas", defaultValue = "false") boolean cobradas, Model model) {
		logger.debug("Inicio morosidad: temporadaId={}, cobradas={}", temporadaId, cobradas);
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);
		model.addAttribute("temporadas", morosidadService.temporadasAnteriores());
		model.addAttribute("temporadaId", temporadaId);
		model.addAttribute("cobradas", cobradas);

		try {
			model.addAttribute("morosidad", morosidadService.obtener(temporadaId, cobradas));
		} catch (IllegalArgumentException e) {
			logger.warn("No se puede mostrar la morosidad: {}", e.getMessage());
			model.addAttribute("error", e.getMessage());
			model.addAttribute("morosidad", new MorosidadDTO(java.util.List.of(), java.util.List.of()));
		}

		return "admin/pagos-morosidad";
	}

	@PostMapping("/cobrar")
	public String cobrar(@RequestParam Long cuotaId, @RequestParam(required = false) Long temporadaId,
			@RequestParam(name = "cobradas", defaultValue = "false") boolean cobradas, RedirectAttributes redirect) {
		return ejecutar("cobrar", cuotaId, temporadaId, cobradas, redirect, () -> {
			morosidadService.cobrar(cuotaId);
			return "Cuota cobrada: se ha registrado el pago por el importe pendiente.";
		});
	}

	@PostMapping("/incobrable")
	public String incobrable(@RequestParam Long cuotaId, @RequestParam(required = false) Long temporadaId,
			@RequestParam(name = "cobradas", defaultValue = "false") boolean cobradas, RedirectAttributes redirect) {
		return ejecutar("marcar incobrable", cuotaId, temporadaId, cobradas, redirect, () -> {
			morosidadService.marcarIncobrable(cuotaId);
			return "Cuota marcada como incobrable.";
		});
	}

	@PostMapping("/deshacer-incobrable")
	public String deshacerIncobrable(@RequestParam Long cuotaId, @RequestParam(required = false) Long temporadaId,
			@RequestParam(name = "cobradas", defaultValue = "false") boolean cobradas, RedirectAttributes redirect) {
		return ejecutar("deshacer incobrable", cuotaId, temporadaId, cobradas, redirect, () -> {
			morosidadService.deshacerIncobrable(cuotaId);
			return "La cuota vuelve a estar pendiente de cobro.";
		});
	}

	@GetMapping("/exportar")
	public ResponseEntity<byte[]> exportar(@RequestParam(name = "temporadaId", required = false) Long temporadaId,
			@RequestParam(name = "cobradas", defaultValue = "false") boolean cobradas) {
		logger.debug("Inicio exportar morosidad: temporadaId={}, cobradas={}", temporadaId, cobradas);

		try {
			// BOM para que Excel abra el UTF-8 con sus tildes
			byte[] csv = ("﻿" + morosidadService.exportarCsv(temporadaId, cobradas))
					.getBytes(StandardCharsets.UTF_8);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"morosidad"
							+ (temporadaId == null ? "" : "-" + temporadaId) + ".csv\"")
					.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv);
		} catch (IllegalArgumentException e) {
			logger.warn("No se puede exportar la morosidad: {}", e.getMessage());

			return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
					.body(e.getMessage().getBytes(StandardCharsets.UTF_8));
		}
	}

	private interface Accion {
		String ejecutar();
	}

	private String ejecutar(String nombre, Long cuotaId, Long temporadaId, boolean cobradas,
			RedirectAttributes redirect, Accion accion) {
		logger.debug("Inicio {}: cuotaId={}", nombre, cuotaId);

		try {
			redirect.addFlashAttribute("mensaje", accion.ejecutar());
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido {}: {}", nombre, e.getMessage());
			redirect.addFlashAttribute("error", e.getMessage());
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al {}: {}", nombre, e.getMessage(), e);
			redirect.addFlashAttribute("error", MENSAJE_ERROR_BASE_DE_DATOS);
		}

		return "redirect:/admin/pagos/morosidad" + consulta(temporadaId, cobradas);
	}

	private static String consulta(Long temporadaId, boolean cobradas) {
		StringBuilder q = new StringBuilder();

		if (temporadaId != null) {
			q.append("?temporadaId=").append(temporadaId);
		}

		if (cobradas) {
			q.append(q.length() == 0 ? '?' : '&').append("cobradas=true");
		}

		return q.toString();
	}
}
