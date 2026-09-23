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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoForm;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;

@Controller
@RequestMapping("/admin")
public class CuerpoTecnicoAdminController {

	private static final Logger logger = LoggerFactory.getLogger(CuerpoTecnicoAdminController.class);

	private final CuerpoTecnicoService cuerpoTecnicoService;

	private final EquipoService equiposService;

	public CuerpoTecnicoAdminController(CuerpoTecnicoService cuerpoTecnicoService, EquipoService equiposService) {
		this.cuerpoTecnicoService = cuerpoTecnicoService;
		this.equiposService = equiposService;
	}

	// Método para listar el cuerpo técnico
	@GetMapping("/cuerpo-tecnico")
	public String listarCuerpoTecnico(Model model) {
		logger.debug("Inicio listarCuerpoTecnico");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);

		// El filtro de categoría se aplica en el navegador (ver cuerpo-tecnico.html)
		List<CuerpoTecnicoDTO> cuerpoTecnico = cuerpoTecnicoService.obtenerTodos();

		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("cuerpoTecnico", cuerpoTecnico);

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarCuerpoTecnico: total={}", cuerpoTecnico.size());
		return "admin/cuerpo-tecnico"; // Retornar la vista para listar el cuerpo técnico
	}

	// Método para guardar un nuevo miembro del cuerpo técnico
	@PostMapping("/cuerpo-tecnico/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarCuerpoTecnico(
			@ModelAttribute CuerpoTecnicoForm cuerpoTecnicoForm) {
		logger.debug("Inicio guardarCuerpoTecnico: id={}, nombre={}", cuerpoTecnicoForm.getId(),
				cuerpoTecnicoForm.getNombre());
		Map<String, String> response = new HashMap<>();
		try {
			CuerpoTecnicoDTO staff = new CuerpoTecnicoDTO();
			if (cuerpoTecnicoForm.getId() != null) {
				staff.setId(cuerpoTecnicoForm.getId());
			}
			staff.setNombre(cuerpoTecnicoForm.getNombre());
			staff.setApellidos(cuerpoTecnicoForm.getApellidos());

			EquipoDTO e = equiposService.obtenerEquipoPorId(cuerpoTecnicoForm.getEquipo());
			if (e == null) {
				logger.warn("Equipo inexistente al guardar cuerpo tecnico: equipoId={}", cuerpoTecnicoForm.getEquipo());
				response.put("error", "El equipo seleccionado no existe.");
				logger.debug("Fin guardarCuerpoTecnico: resultado=EQUIPO_INVALIDO");
				return ResponseEntity.badRequest().body(response);
			}

			staff.setCategoria(e.getCategoria());
			staff.setEquipo(e.getNombre());
			staff.setDeporte(e.getDeporte());

			staff.setPuesto(cuerpoTecnicoForm.getPuesto());

			// Si hay una foto cargada, validarla y convertirla a byte[]
			if (!cuerpoTecnicoForm.getFoto().isEmpty()) {
				String errorImagen = AdminViewSupport.validarImagen(cuerpoTecnicoForm.getFoto(),
						AdminViewSupport.TAMANO_MAXIMO_FOTO);
				if (errorImagen != null) {
					logger.warn("Foto de cuerpo tecnico invalida: nombre={}, motivo={}", cuerpoTecnicoForm.getNombre(),
							errorImagen);
					response.put("error", errorImagen);
					logger.debug("Fin guardarCuerpoTecnico: resultado=FOTO_INVALIDA");
					return ResponseEntity.badRequest().body(response);
				}
				staff.setFoto(cuerpoTecnicoForm.getFoto().getBytes());
			} else if (cuerpoTecnicoForm.getId() != null) {
				// Editando sin subir foto nueva: se conserva la foto ya guardada
				CuerpoTecnicoDTO existente = cuerpoTecnicoService.obtenerCuerpoTecnicoPorId(cuerpoTecnicoForm.getId());
				if (existente != null) {
					staff.setFoto(existente.getFoto());
				}
			}

			if (cuerpoTecnicoService.guardarCuerpoTecnico(staff)) {
				response.put("mensaje", "Cuerpo técnico guardado correctamente.");
				logger.debug("Fin guardarCuerpoTecnico: resultado=OK");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta al cuerpo técnico.");
				logger.warn("No se pudo dar de alta al cuerpo tecnico: nombre={}", cuerpoTecnicoForm.getNombre());
				logger.debug("Fin guardarCuerpoTecnico: resultado=FALLIDO");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar el cuerpo técnico: {}", e.getMessage(), e);
			response.put("error", "Error al guardar el cuerpo técnico");
			logger.debug("Fin guardarCuerpoTecnico: resultado=ERROR");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un miembro del cuerpo técnico por su ID
	@PostMapping("/cuerpo-tecnico/borrar/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> borrarCuerpoTecnico(@PathVariable Long id) {
		logger.debug("Inicio borrarCuerpoTecnico: id={}", id);
		Map<String, String> response = new HashMap<>();
		try {
			cuerpoTecnicoService.eliminarCuerpoTecnico(id);
			response.put("mensaje", "Miembro del cuerpo técnico eliminado correctamente.");
			logger.debug("Fin borrarCuerpoTecnico: id={}, resultado=OK", id);
			return ResponseEntity.ok(response);
		} catch (IllegalStateException e) {
			logger.warn("No se ha podido borrar el cuerpo tecnico id={}: {}", id, e.getMessage());
			response.put("error", e.getMessage());
			logger.debug("Fin borrarCuerpoTecnico: id={}, resultado=BLOQUEADO", id);
			return ResponseEntity.badRequest().body(response);
		} catch (Exception e) {
			logger.error("Error al borrar el cuerpo técnico id={}: {}", id, e.getMessage(), e);
			response.put("error", "No se ha podido eliminar el miembro del cuerpo técnico.");
			logger.debug("Fin borrarCuerpoTecnico: id={}, resultado=ERROR", id);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
