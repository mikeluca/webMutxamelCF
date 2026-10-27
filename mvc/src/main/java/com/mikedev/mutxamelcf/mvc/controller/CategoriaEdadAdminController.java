package com.mikedev.mutxamelcf.mvc.controller;

import java.util.HashMap;
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

import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.EquipoService;

/**
 * Ver y editar CATEGORIAS_EDAD: el rango de edad de cada categoría (edad = año de inicio de la temporada − año de
 * nacimiento). Solo SUPER y ADMIN (ver SecurityConfig).
 */
@Controller
@RequestMapping("/admin/categorias-edad")
public class CategoriaEdadAdminController {

	private static final Logger logger = LoggerFactory.getLogger(CategoriaEdadAdminController.class);

	private final CategoriaPorEdad categoriaPorEdad;
	private final EquipoService equipoService;

	public CategoriaEdadAdminController(CategoriaPorEdad categoriaPorEdad, EquipoService equipoService) {
		this.categoriaPorEdad = categoriaPorEdad;
		this.equipoService = equipoService;
	}

	@GetMapping
	public String listar(Model model) {
		model.addAttribute("categoriasEdad", categoriaPorEdad.obtenerTodas());
		model.addAttribute("categoriasEquipos", equipoService.obtenerCategorias());
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);
		return "admin/categorias-edad";
	}

	@PostMapping("/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardar(@RequestParam String categoria,
			@RequestParam(name = "edadMin") Integer edadMin, @RequestParam(name = "edadMax") Integer edadMax,
			@RequestParam(name = "orden") Integer orden) {
		logger.debug("Inicio guardar categoria edad: categoria={}", categoria);
		Map<String, String> response = new HashMap<>();
		try {
			categoriaPorEdad.guardar(new CategoriaEdad(categoria, edadMin, edadMax, orden));
			response.put("mensaje", "Categoría guardada.");
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException e) {
			response.put("error", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al guardar la categoría por edad: {}", e.getMessage(), e);
			response.put("error", "No se ha podido guardar: error de base de datos. Revisa los registros del servidor.");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/borrar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> borrar(@RequestParam String categoria) {
		Map<String, String> response = new HashMap<>();
		try {
			categoriaPorEdad.eliminar(categoria);
			response.put("mensaje", "Categoría eliminada.");
			return ResponseEntity.ok(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al borrar la categoría por edad: {}", e.getMessage(), e);
			response.put("error", "No se ha podido borrar: error de base de datos. Revisa los registros del servidor.");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
