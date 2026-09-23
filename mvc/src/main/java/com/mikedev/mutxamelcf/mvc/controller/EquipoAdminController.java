package com.mikedev.mutxamelcf.mvc.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.EquipoService;

@Controller
@RequestMapping("/admin")
public class EquipoAdminController {

	private static final Logger logger = LoggerFactory.getLogger(EquipoAdminController.class);

	private final EquipoService equiposService;

	public EquipoAdminController(EquipoService equiposService) {
		this.equiposService = equiposService;
	}

	// Método para listar todos los equipos
	@GetMapping("/equipos")
	public String listarEquipos(Model model) {
		logger.debug("Inicio listarEquipos");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<String> categorias = equiposService.obtenerCategorias(); // Obtener categorías de equipos

		// El filtro de categoría se aplica en el navegador (ver equipos.html)
		List<EquipoDTO> equipos = listaEquipos;

		model.addAttribute("categorias", categorias);
		model.addAttribute("equipos", equipos);

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarEquipos: total={}", equipos.size());
		return "admin/equipos"; // Retornar la vista para listar equipos
	}

	@PostMapping("/equipos/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarEquipo(@RequestParam(required = false) Long id,
			@RequestParam String categoria, @RequestParam String nombre, @RequestParam String grupo,
			@RequestParam String deporte) {
		logger.debug("Inicio guardarEquipo: id={}, categoria={}, nombre={}, grupo={}, deporte={}", id, categoria,
				nombre, grupo, deporte);
		Map<String, String> response = new HashMap<>();
		try {
			EquipoDTO equipo = new EquipoDTO();
			equipo.setId(id);
			equipo.setCategoria(categoria);
			equipo.setNombre(nombre);
			equipo.setGrupo(grupo);
			equipo.setDeporte(deporte);

			if (equiposService.guardar(equipo)) {
				response.put("mensaje", "Equipo guardado correctamente.");
				logger.debug("Fin guardarEquipo: resultado=OK");
				return ResponseEntity.ok(response);
			} else {
				logger.warn("El equipo ya existe: categoria={}, nombre={}, grupo={}", categoria, nombre, grupo);
				response.put("error", "Error. El equipo ya existe.");
				logger.debug("Fin guardarEquipo: resultado=DUPLICADO");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar el equipo: {}", e.getMessage(), e);
			response.put("error", "Error al guardar el equipo");
			logger.debug("Fin guardarEquipo: resultado=ERROR");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un equipo por su ID
	@PostMapping("/equipos/borrar/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> borrarEquipo(@PathVariable Long id) {
		logger.debug("Inicio borrarEquipo: id={}", id);
		Map<String, String> response = new HashMap<>();
		try {
			equiposService.eliminarEquipo(id);
			response.put("mensaje", "Equipo eliminado correctamente.");
			logger.debug("Fin borrarEquipo: id={}, resultado=OK", id);
			return ResponseEntity.ok(response);
		} catch (IllegalStateException e) {
			logger.warn("No se ha podido borrar el equipo id={}: {}", id, e.getMessage());
			response.put("error", e.getMessage());
			logger.debug("Fin borrarEquipo: id={}, resultado=BLOQUEADO", id);
			return ResponseEntity.badRequest().body(response);
		} catch (Exception e) {
			logger.error("Error al borrar el equipo id={}: {}", id, e.getMessage(), e);
			response.put("error", "No se ha podido eliminar el equipo.");
			logger.debug("Fin borrarEquipo: id={}, resultado=ERROR", id);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
