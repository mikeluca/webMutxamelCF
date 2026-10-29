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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.DevolucionRepartoRequest;
import com.mikedev.mutxamelcf.model.ResultadoDevolucionDTO;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;
import com.mikedev.mutxamelcf.service.RepartoService;

/**
 * Reparto de septiembre en el panel: de los jugadores del equipo de pretemporada (y las pruebas) a los equipos
 * definitivos de cada categoría. Solo SUPER y ADMIN (ver SecurityConfig). Las acciones devuelven JSON porque las
 * llama la propia página.
 */
@Controller
@RequestMapping("/admin/reparto")
public class RepartoAdminController {

	private static final Logger logger = LoggerFactory.getLogger(RepartoAdminController.class);

	static final String MENSAJE_ERROR_BASE_DE_DATOS =
			"No se ha podido completar la operación: error de base de datos. Revisa los registros del servidor.";

	private final RepartoService repartoService;

	public RepartoAdminController(RepartoService repartoService) {
		this.repartoService = repartoService;
	}

	@GetMapping
	public String pagina(@RequestParam(name = "categoria", required = false) String categoria, Model model) {
		logger.debug("Inicio pagina reparto: categoria={}", categoria);
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		try {
			List<String> categorias = repartoService.categorias();
			model.addAttribute("categorias", categorias);

			String elegida = categoria != null && !categoria.isBlank() ? categoria
					: (categorias.isEmpty() ? null : categorias.get(0));

			if (elegida != null) {
				RepartoCategoriaDTO reparto = repartoService.obtenerCategoria(elegida);
				model.addAttribute("categoria", reparto.categoria());
				model.addAttribute("reparto", reparto);
			}
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se puede mostrar el reparto: {}", e.getMessage());
			model.addAttribute("error", e.getMessage());
		}

		return "admin/reparto";
	}

	@PostMapping("/asignar")
	@org.springframework.web.bind.annotation.ResponseBody
	public ResponseEntity<Map<String, Object>> asignar(@RequestBody AsignacionRepartoRequest request) {
		logger.debug("Inicio asignar: equipoId={}", request == null ? null : request.equipoId());

		try {
			ResultadoRepartoDTO resultado = repartoService.asignar(request);
			Map<String, Object> respuesta = new HashMap<>();
			respuesta.put("mensaje", resultado.asignados() + " jugador(es) asignado(s)."
					+ (resultado.equipoActivado() ? " El equipo queda activo en la temporada." : "")
					+ (resultado.pretemporadaVaciada() ? " El equipo de pretemporada se queda vacío y se desactiva." : ""));
			respuesta.put("avisos", resultado.avisos());
			return ResponseEntity.ok(respuesta);
		} catch (PruebasSinConfirmarException e) {
			// Hay que confirmar que las pruebas pasan a CONFIRMADO: la página lo pregunta y repite con la confirmación
			Map<String, Object> respuesta = new HashMap<>();
			respuesta.put("requiereConfirmacion", true);
			respuesta.put("error", e.getMessage());
			respuesta.put("pruebas", e.getPruebas());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido asignar: {}", e.getMessage());
			return error(e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al asignar: {}", e.getMessage(), e);
			return error(MENSAJE_ERROR_BASE_DE_DATOS, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/devolver")
	@org.springframework.web.bind.annotation.ResponseBody
	public ResponseEntity<Map<String, Object>> devolver(@RequestBody DevolucionRepartoRequest request) {
		logger.debug("Inicio devolver a la pretemporada");

		try {
			ResultadoDevolucionDTO resultado = repartoService.devolverAPretemporada(request);
			Map<String, Object> respuesta = new HashMap<>();
			respuesta.put("mensaje", resultado.devueltos() + " jugador(es) devuelto(s) a la pretemporada."
					+ (resultado.pretemporadaReactivada() ? " El equipo de pretemporada vuelve a estar activo." : ""));
			respuesta.put("avisos", resultado.avisos());
			return ResponseEntity.ok(respuesta);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido devolver a la pretemporada: {}", e.getMessage());
			return error(e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al devolver a la pretemporada: {}", e.getMessage(), e);
			return error(MENSAJE_ERROR_BASE_DE_DATOS, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/equipos")
	@org.springframework.web.bind.annotation.ResponseBody
	public ResponseEntity<Map<String, Object>> crearEquipo(@RequestParam String categoria) {
		logger.debug("Inicio crearEquipo: categoria={}", categoria);

		try {
			EquipoRepartoDTO equipo = repartoService.crearEquipo(categoria);
			Map<String, Object> respuesta = new HashMap<>();
			respuesta.put("mensaje", "Equipo " + equipo.nombre() + " creado. Se activará al asignarle el primer jugador.");
			respuesta.put("equipoId", equipo.equipoId());
			return ResponseEntity.ok(respuesta);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido crear el equipo: {}", e.getMessage());
			return error(e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al crear el equipo: {}", e.getMessage(), e);
			return error(MENSAJE_ERROR_BASE_DE_DATOS, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static ResponseEntity<Map<String, Object>> error(String mensaje, HttpStatus estado) {
		Map<String, Object> respuesta = new HashMap<>();
		respuesta.put("error", mensaje);
		return ResponseEntity.status(estado).body(respuesta);
	}
}
