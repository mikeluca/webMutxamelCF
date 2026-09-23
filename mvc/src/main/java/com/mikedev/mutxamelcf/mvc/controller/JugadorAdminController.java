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

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

@Controller
@RequestMapping("/admin")
public class JugadorAdminController {

	private static final Logger logger = LoggerFactory.getLogger(JugadorAdminController.class);

	private final JugadorService jugadoresService;

	private final FamiliarService familiarService;

	private final EquipoService equiposService;

	public JugadorAdminController(JugadorService jugadoresService, FamiliarService familiarService,
			EquipoService equiposService) {
		this.jugadoresService = jugadoresService;
		this.familiarService = familiarService;
		this.equiposService = equiposService;
	}

	// Método para listar jugadores
	@GetMapping("/jugadores")
	public String listarJugadores(Model model) {
		logger.debug("Inicio listarJugadores");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);

		// El filtro de categoría se aplica en el navegador (ver jugadores.html)
		List<JugadorDTO> jugadores = jugadoresService.obtenerTodos();

		List<String> categorias = equiposService.obtenerCategorias();
		List<FamiliarDTO> listaFamiliares = familiarService.obtenerTodos();
		model.addAttribute("categorias", categorias);
		model.addAttribute("jugadores", jugadores);
		model.addAttribute("listaFamiliares", listaFamiliares);

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarJugadores: total={}", jugadores.size());
		return "admin/jugadores"; // Retornar la vista para listar jugadores
	}

	// Método para guardar un nuevo jugador
	@PostMapping("/jugadores/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarJugador(@ModelAttribute JugadorForm jugadorForm) {
		logger.debug("Inicio guardarJugador: id={}, nombre={}", jugadorForm.getId(), jugadorForm.getNombre());
		Map<String, String> response = new HashMap<>();

		try {
			JugadorDTO jugador = new JugadorDTO();
			if (jugadorForm.getId() != null) {
				jugador.setId(jugadorForm.getId());
			}
			jugador.setNombre(jugadorForm.getNombre());
			jugador.setApellidos(jugadorForm.getApellidos());

			EquipoDTO e = equiposService.obtenerEquipoPorId(jugadorForm.getEquipo());
			if (e == null) {
				logger.warn("Equipo inexistente al guardar jugador: equipoId={}", jugadorForm.getEquipo());
				response.put("error", "El equipo seleccionado no existe.");
				logger.debug("Fin guardarJugador: resultado=EQUIPO_INVALIDO");
				return ResponseEntity.badRequest().body(response);
			}
			jugador.setCategoria(e.getCategoria());
			jugador.setEquipo(e.getNombre());
			jugador.setDeporte(e.getDeporte());
			if (jugadorForm.getDorsal() == null) {
				jugador.setDorsal(null); // Si dorsal es null, simplemente asigna null
			} else {
				jugador.setDorsal(jugadorForm.getDorsal()); // Asigna el valor de dorsal si no es null
			}
			jugador.setPosicion(jugadorForm.getPosicion());

			// Si hay una foto cargada, validarla y convertirla a byte[]
			if (!jugadorForm.getFoto().isEmpty()) {
				String errorImagen = AdminViewSupport.validarImagen(jugadorForm.getFoto(),
						AdminViewSupport.TAMANO_MAXIMO_FOTO);
				if (errorImagen != null) {
					logger.warn("Foto de jugador invalida: nombre={}, motivo={}", jugadorForm.getNombre(), errorImagen);
					response.put("error", errorImagen);
					logger.debug("Fin guardarJugador: resultado=FOTO_INVALIDA");
					return ResponseEntity.badRequest().body(response);
				}
				jugador.setFoto(jugadorForm.getFoto().getBytes());
			} else if (jugadorForm.getId() != null) {
				// Editando sin subir foto nueva: se conserva la foto ya guardada
				JugadorDTO existente = jugadoresService.obtenerJugadorPorId(jugadorForm.getId());
				if (existente != null) {
					jugador.setFoto(existente.getFoto());
				}
			}

			if (jugadoresService.guardarJugador(jugador)) {
				response.put("mensaje", "Jugador guardado correctamente.");
				logger.debug("Fin guardarJugador: resultado=OK");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta al jugador.");
				logger.warn("No se pudo dar de alta al jugador: nombre={}", jugadorForm.getNombre());
				logger.debug("Fin guardarJugador: resultado=FALLIDO");
				return ResponseEntity.badRequest().body(response);
			}

		} catch (Exception e) {
			logger.error("Error al guardar el jugador: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar el jugador");
			logger.debug("Fin guardarJugador: resultado=ERROR");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un jugador por su ID
	@PostMapping("/jugadores/borrar/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> borrarJugador(@PathVariable Long id) {
		logger.debug("Inicio borrarJugador: id={}", id);
		Map<String, String> response = new HashMap<>();
		try {
			jugadoresService.eliminarJugador(id);
			response.put("mensaje", "Jugador eliminado correctamente.");
			logger.debug("Fin borrarJugador: id={}, resultado=OK", id);
			return ResponseEntity.ok(response);
		} catch (IllegalStateException e) {
			logger.warn("No se ha podido borrar el jugador id={}: {}", id, e.getMessage());
			response.put("error", e.getMessage());
			logger.debug("Fin borrarJugador: id={}, resultado=BLOQUEADO", id);
			return ResponseEntity.badRequest().body(response);
		} catch (Exception e) {
			logger.error("Error al borrar el jugador id={}: {}", id, e.getMessage(), e);
			response.put("error", "No se ha podido eliminar el jugador.");
			logger.debug("Fin borrarJugador: id={}, resultado=ERROR", id);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}
