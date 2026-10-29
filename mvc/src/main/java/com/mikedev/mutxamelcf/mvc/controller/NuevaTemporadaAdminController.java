package com.mikedev.mutxamelcf.mvc.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

/**
 * Asistente "Nueva temporada" del panel. Solo SUPER (ver SecurityConfig). Las acciones devuelven JSON con
 * {@code mensaje} o {@code error} porque las llama la propia página.
 */
@Controller
@RequestMapping("/admin/nueva-temporada")
public class NuevaTemporadaAdminController {

	private static final Logger logger = LoggerFactory.getLogger(NuevaTemporadaAdminController.class);

	static final String MENSAJE_ERROR_BASE_DE_DATOS =
			"No se ha podido completar la operación: error de base de datos. Revisa los registros del servidor.";

	private final NuevaTemporadaService nuevaTemporadaService;
	private final EquipoService equipoService;

	public NuevaTemporadaAdminController(NuevaTemporadaService nuevaTemporadaService, EquipoService equipoService) {
		this.nuevaTemporadaService = nuevaTemporadaService;
		this.equipoService = equipoService;
	}

	@GetMapping
	public String pagina(@RequestParam(name = "temporadaId", required = false) Long temporadaId, Model model) {
		logger.debug("Inicio pagina nueva temporada: temporadaId={}", temporadaId);

		Temporada activa = nuevaTemporadaService.temporadaActiva();
		List<Temporada> destinos = nuevaTemporadaService.temporadasDestino();
		model.addAttribute("activa", activa);
		model.addAttribute("destinos", destinos);
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		Temporada destino = temporadaId == null ? null
				: destinos.stream().filter(t -> temporadaId.equals(t.getId())).findFirst().orElse(null);

		if (temporadaId != null && destino == null) {
			model.addAttribute("error", "La temporada elegida no es un destino válido: tiene que existir, no estar activa y "
					+ "empezar después de la activa.");
		}

		if (destino != null) {
			List<EquipoDTO> equipos = equipoService.obtenerTodosConEstadoTemporada();
			model.addAttribute("destino", destino);
			model.addAttribute("listaEquipos", equipos);
			model.addAttribute("revision", nuevaTemporadaService.revision(destino.getId()));
			model.addAttribute("resumen", nuevaTemporadaService.resumenAplicacion(destino.getId()));
		}

		return "admin/nueva-temporada";
	}

	@PostMapping("/generar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> generar(@RequestParam Long temporadaId) {
		return ejecutar("generar propuesta", () -> {
			int creadas = nuevaTemporadaService.generarPropuesta(temporadaId);
			return "Propuesta generada: " + creadas + " jugador(es) añadido(s) al borrador.";
		});
	}

	@PostMapping("/regenerar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> regenerar(@RequestParam Long temporadaId,
			@RequestParam(name = "confirmado", defaultValue = "false") boolean confirmado) {
		if (!confirmado) {
			return error("Regenerar borra el borrador entero: hay que confirmarlo.", HttpStatus.BAD_REQUEST);
		}
		return ejecutar("regenerar propuesta", () -> {
			int creadas = nuevaTemporadaService.regenerar(temporadaId);
			return "Borrador regenerado: " + creadas + " jugador(es).";
		});
	}

	@PostMapping("/fila/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarFila(@RequestParam Long temporadaId, @RequestParam Long jugadorId,
			@RequestParam(name = "equipo", required = false) Long equipoId,
			@RequestParam(name = "dorsal", required = false) Integer dorsal,
			@RequestParam(name = "baja", defaultValue = "false") boolean baja,
			@RequestParam(name = "anioNacimiento", required = false) Integer anioNacimiento) {
		return ejecutar("guardar fila", () -> {
			nuevaTemporadaService.guardarFila(temporadaId, jugadorId, equipoId, dorsal, baja, anioNacimiento);
			return "Fila guardada.";
		});
	}

	@PostMapping("/continuidad")
	@ResponseBody
	public ResponseEntity<Map<String, String>> continuidad(@RequestParam Long temporadaId, @RequestParam Long jugadorId,
			@RequestParam String continuidad) {
		return ejecutar("marcar continuidad", () -> {
			nuevaTemporadaService.marcarContinuidad(temporadaId, jugadorId, continuidad);
			return "Continuidad guardada.";
		});
	}

	@PostMapping("/prueba/anadir")
	@ResponseBody
	public ResponseEntity<Map<String, String>> anadirPrueba(@RequestParam Long temporadaId,
			@RequestParam String nombre, @RequestParam String apellidos,
			@RequestParam(name = "anioNacimiento", required = false) Integer anioNacimiento) {
		return ejecutar("añadir prueba", () -> {
			nuevaTemporadaService.anadirPrueba(temporadaId, nombre, apellidos, anioNacimiento);
			return "Jugador de prueba añadido al borrador.";
		});
	}

	@PostMapping("/sin-propuesta/anadir")
	@ResponseBody
	public ResponseEntity<Map<String, String>> anadir(@RequestParam Long temporadaId, @RequestParam Long jugadorId) {
		return ejecutar("añadir sin propuesta", () -> {
			nuevaTemporadaService.anadirSinPropuesta(temporadaId, jugadorId);
			return "Jugador añadido al borrador.";
		});
	}

	@PostMapping("/aplicar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> aplicar(@RequestParam Long temporadaId,
			@RequestParam(name = "confirmacion", defaultValue = "") String confirmacion) {
		return ejecutar("aplicar temporada", () -> {
			nuevaTemporadaService.aplicar(temporadaId, confirmacion);
			return "Temporada aplicada y activada. La anterior queda como historial.";
		});
	}

	private interface Accion {
		String ejecutar();
	}

	private ResponseEntity<Map<String, String>> ejecutar(String nombre, Accion accion) {
		logger.debug("Inicio {}", nombre);
		try {
			Map<String, String> response = new HashMap<>();
			response.put("mensaje", accion.ejecutar());
			logger.debug("Fin {}: resultado=OK", nombre);
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido {}: {}", nombre, e.getMessage());
			return error(e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al {}: {}", nombre, e.getMessage(), e);
			return error(MENSAJE_ERROR_BASE_DE_DATOS, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static ResponseEntity<Map<String, String>> error(String mensaje, HttpStatus estado) {
		Map<String, String> response = new HashMap<>();
		response.put("error", mensaje);
		return ResponseEntity.status(estado).body(response);
	}
}
