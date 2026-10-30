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

import com.mikedev.mutxamelcf.service.EquipacionService;

/**
 * Alta, edición, orden y activación de los equipajes del club. Solo SUPER y ADMIN (ver SecurityConfig). Un equipaje
 * usado en alguna convocatoria no se borra, solo se desactiva.
 */
@Controller
@RequestMapping("/admin/equipajes")
public class EquipacionAdminController {

	private static final Logger logger = LoggerFactory.getLogger(EquipacionAdminController.class);

	private final EquipacionService equipacionService;

	public EquipacionAdminController(EquipacionService equipacionService) {
		this.equipacionService = equipacionService;
	}

	@GetMapping
	public String listar(Model model) {
		model.addAttribute("equipaciones", equipacionService.obtenerTodas());
		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);
		return "admin/equipajes";
	}

	@PostMapping("/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardar(@RequestParam(required = false) Long id,
			@RequestParam String nombre, @RequestParam(required = false) String camiseta,
			@RequestParam(required = false) String pantalon, @RequestParam(required = false) String medias,
			@RequestParam(required = false) String colorHex, @RequestParam(required = false) Integer orden) {
		return ejecutar("guardar", "Equipaje guardado.",
				() -> equipacionService.guardar(id, nombre, camiseta, pantalon, medias, colorHex, orden));
	}

	@PostMapping("/activar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> activar(@RequestParam Long id, @RequestParam boolean activa) {
		return ejecutar("cambiar el estado de", activa ? "Equipaje activado." : "Equipaje desactivado.",
				() -> equipacionService.cambiarActiva(id, activa));
	}

	@PostMapping("/borrar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> borrar(@RequestParam Long id) {
		return ejecutar("borrar", "Equipaje eliminado.", () -> equipacionService.eliminar(id));
	}

	private ResponseEntity<Map<String, String>> ejecutar(String accion, String mensaje, Runnable operacion) {
		Map<String, String> response = new HashMap<>();
		try {
			operacion.run();
			response.put("mensaje", mensaje);
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException e) {
			response.put("error", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al {} el equipaje: {}", accion, e.getMessage(), e);
			response.put("error", "No se ha podido completar la operación: error de base de datos. Revisa los registros del servidor.");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
