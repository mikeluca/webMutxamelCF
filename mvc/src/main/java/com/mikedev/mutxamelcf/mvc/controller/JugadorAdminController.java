package com.mikedev.mutxamelcf.mvc.controller;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.AnioNacimiento;
import com.mikedev.mutxamelcf.model.CambioEquipoJugador;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.PosicionJugador;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.HistorialJugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class JugadorAdminController {

	private static final Logger logger = LoggerFactory.getLogger(JugadorAdminController.class);

	static final String MENSAJE_ERROR_BASE_DE_DATOS =
			"No se ha podido guardar: error de base de datos. Revisa los registros del servidor.";

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
	public String listarJugadores(@RequestParam(name = "estado", defaultValue = "activos") String estado, Model model) {
		logger.debug("Inicio listarJugadores: estado={}", estado);
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		// Las opciones del desplegable "Posicion" (la misma lista cerrada que usa la app)
		model.addAttribute("posiciones", PosicionJugador.textos());

		// El filtro de categoría se aplica en el navegador (ver jugadores.html); el de estado, aquí.
		String filtro = "bajas".equals(estado) || "todos".equals(estado) ? estado : "activos";
		List<JugadorDTO> jugadores = switch (filtro) {
		case "bajas" -> jugadoresService.obtenerBajas();
		case "todos" -> jugadoresService.obtenerTodos();
		default -> jugadoresService.obtenerActivos();
		};
		model.addAttribute("estado", filtro);

		List<String> categorias = equiposService.obtenerCategorias();
		List<FamiliarDTO> listaFamiliares = familiarService.obtenerTodos();
		model.addAttribute("categorias", categorias);
		model.addAttribute("jugadores", jugadores);
		model.addAttribute("listaFamiliares", listaFamiliares);


		logger.debug("Fin listarJugadores: total={}", jugadores.size());
		return "admin/jugadores"; // Retornar la vista para listar jugadores
	}

	// Método para guardar un nuevo jugador
	@PostMapping("/jugadores/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarJugador(@Valid @ModelAttribute JugadorForm jugadorForm,
			BindingResult bindingResult) {
		logger.debug("Inicio guardarJugador: id={}, nombre={}", jugadorForm.getId(), jugadorForm.getNombre());
		Map<String, String> response = new HashMap<>();

		if (bindingResult.hasErrors()) {
			String mensaje = bindingResult.getFieldErrors().stream()
					.map(FieldError::getDefaultMessage)
					.findFirst()
					.orElse("Datos del jugador no válidos.");
			response.put("error", mensaje);
			logger.warn("Guardar jugador rechazado por validación: id={}, motivo={}", jugadorForm.getId(), mensaje);
			logger.debug("Fin guardarJugador: resultado=DATOS_INVALIDOS");
			return ResponseEntity.badRequest().body(response);
		}

		// Opcional; no se escribe en el log (dato de un menor).
		String errorAnio = AnioNacimiento.mensajeError(jugadorForm.getAnioNacimiento(), Year.now().getValue());
		if (errorAnio != null) {
			response.put("error", errorAnio);
			logger.warn("Guardar jugador rechazado: año de nacimiento fuera de rango, id={}", jugadorForm.getId());
			logger.debug("Fin guardarJugador: resultado=ANIO_INVALIDO");
			return ResponseEntity.badRequest().body(response);
		}

		try {
			JugadorDTO jugador = new JugadorDTO();
			if (jugadorForm.getId() != null) {
				jugador.setId(jugadorForm.getId());
			}
			jugador.setNombre(jugadorForm.getNombre());
			jugador.setApellidos(jugadorForm.getApellidos());
			jugador.setAnioNacimiento(jugadorForm.getAnioNacimiento());
			// Opcional: en blanco se guarda vacío (NULL) y en el directo sale el nombre y los apellidos.
			String nombreRetransmision = jugadorForm.getNombreRetransmision() == null ? ""
					: jugadorForm.getNombreRetransmision().trim();
			jugador.setNombreRetransmision(nombreRetransmision.isEmpty() ? null : nombreRetransmision);

			EquipoDTO e = jugadorForm.getEquipo() == null ? null
					: equiposService.obtenerEquipoPorId(jugadorForm.getEquipo());
			if (e == null) {
				logger.warn("Equipo inexistente al guardar jugador: equipoId={}", jugadorForm.getEquipo());
				response.put("error", "El equipo seleccionado no existe.");
				logger.debug("Fin guardarJugador: resultado=EQUIPO_INVALIDO");
				return ResponseEntity.badRequest().body(response);
			}
			// El enlace es el ID; el texto, la categoría y el deporte se copian del equipo para que no se desincronicen.
			jugador.setEquipoId(e.getId());
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

		} catch (DorsalRepetidoException e) {
			// "El dorsal 7 ya lo tiene Nombre Apellido": el panel lo muestra tal cual en el modal
			logger.warn("Guardar jugador rechazado: {}", e.getMessage());
			response.put("error", e.getMessage());
			logger.debug("Fin guardarJugador: resultado=DORSAL_REPETIDO");
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al guardar el jugador: {}", e.getMessage(), e);
			response.put("error", MENSAJE_ERROR_BASE_DE_DATOS);
			logger.debug("Fin guardarJugador: resultado=ERROR_BD");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		} catch (Exception e) {
			logger.error("Error al guardar el jugador: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar el jugador");
			logger.debug("Fin guardarJugador: resultado=ERROR");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/** FECHA_BAJA = hoy, sin equipo y BAJA = 1 en la temporada activa. Conserva todo el historial. */
	@PostMapping("/jugadores/baja/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> darDeBaja(@PathVariable Long id) {
		logger.debug("Inicio darDeBaja: id={}", id);
		Map<String, String> response = new HashMap<>();
		try {
			jugadoresService.darDeBaja(id);
			response.put("mensaje", "Jugador dado de baja correctamente.");
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido dar de baja al jugador id={}: {}", id, e.getMessage());
			response.put("error", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al dar de baja al jugador id={}: {}", id, e.getMessage(), e);
			response.put("error", MENSAJE_ERROR_BASE_DE_DATOS);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/** Quita la baja; hay que elegir equipo. */
	@PostMapping("/jugadores/reactivar/{id}")
	@ResponseBody
	public ResponseEntity<Map<String, String>> reactivar(@PathVariable Long id,
			@RequestParam(name = "equipo", required = false) Long equipoId) {
		logger.debug("Inicio reactivar: id={}, equipoId={}", id, equipoId);
		Map<String, String> response = new HashMap<>();
		try {
			jugadoresService.reactivar(id, equipoId);
			response.put("mensaje", "Jugador reactivado correctamente.");
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException | IllegalStateException e) {
			logger.warn("No se ha podido reactivar al jugador id={}: {}", id, e.getMessage());
			response.put("error", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (DataAccessException e) {
			logger.error("Error de base de datos al reactivar al jugador id={}: {}", id, e.getMessage(), e);
			response.put("error", MENSAJE_ERROR_BASE_DE_DATOS);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/** Historial del jugador por temporada (ficha del panel). */
	@GetMapping("/jugadores/{id}/historial")
	@ResponseBody
	public ResponseEntity<List<HistorialJugadorDTO>> historial(@PathVariable Long id) {
		return ResponseEntity.ok(jugadoresService.obtenerHistorial(id));
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

	// ---------------------------------------------------------------------------------------------------------------
	// Acciones en bloque. Cada jugador se procesa en su propia transaccion (cada llamada al servicio es una): si uno
	// falla, los demas siguen.
	// ---------------------------------------------------------------------------------------------------------------

	/** Maximo de ids por peticion en bloque. */
	static final int MAXIMO_IDS_BLOQUE = 200;

	/** Un jugador del resumen; {@code motivo} solo si hay algo que contar (por que no, o el aviso). */
	public record JugadorResumen(Long id, String nombre, String motivo) {
	}

	/**
	 * Resumen de una accion en bloque. {@code correctos}: hechos sin mas; {@code conAviso}: hechos con una salvedad (p. ej.
	 * movido sin dorsal); {@code saltados}: no hacia falta o no se puede mover (ya de baja...); {@code errores}: no se ha
	 * podido, con su motivo.
	 */
	public record ResumenBloque(List<JugadorResumen> correctos, List<JugadorResumen> conAviso,
			List<JugadorResumen> saltados, List<JugadorResumen> errores) {
	}

	public record IdsRequest(List<Long> ids) {
	}

	public record CambiarEquipoRequest(List<Long> ids, Long equipoId) {
	}

	private static final class Bloque {
		final List<JugadorResumen> correctos = new ArrayList<>();
		final List<JugadorResumen> conAviso = new ArrayList<>();
		final List<JugadorResumen> saltados = new ArrayList<>();
		final List<JugadorResumen> errores = new ArrayList<>();

		ResumenBloque resumen() {
			return new ResumenBloque(correctos, conAviso, saltados, errores);
		}
	}

	/** Los ids sin repetidos ni nulos; null si la lista esta vacia o pasa de {@link #MAXIMO_IDS_BLOQUE}. */
	private static List<Long> idsValidos(List<Long> ids) {
		if (ids == null) {
			return null;
		}
		List<Long> limpios = ids.stream().filter(Objects::nonNull).distinct().toList();
		return limpios.isEmpty() || limpios.size() > MAXIMO_IDS_BLOQUE ? null : limpios;
	}

	private static ResponseEntity<Map<String, String>> idsNoValidos() {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "Hay que indicar entre 1 y " + MAXIMO_IDS_BLOQUE + " jugadores."));
	}

	private String nombreDe(Long id) {
		JugadorDTO jugador = jugadoresService.obtenerJugadorPorId(id);
		return jugador == null ? null : (jugador.getNombre() + " " + jugador.getApellidos()).trim();
	}

	private static String motivo(Exception e) {
		return e.getMessage() == null || e.getMessage().isBlank() ? "error inesperado" : e.getMessage();
	}

	/** Borra a cada jugador con la misma regla que el borrado individual; los que no se pueden borrar se cuentan aparte. */
	@PostMapping("/jugadores/borrar-multiple")
	@ResponseBody
	public ResponseEntity<?> borrarMultiple(@RequestBody IdsRequest peticion) {
		List<Long> ids = idsValidos(peticion == null ? null : peticion.ids());
		if (ids == null) {
			return idsNoValidos();
		}
		logger.debug("Inicio borrarMultiple: total={}", ids.size());
		Bloque bloque = new Bloque();
		for (Long id : ids) {
			String nombre = nombreDe(id);
			if (nombre == null) {
				bloque.errores.add(new JugadorResumen(id, "Jugador " + id, "el jugador no existe"));
				continue;
			}
			try {
				jugadoresService.eliminarJugador(id);
				bloque.correctos.add(new JugadorResumen(id, nombre, null));
			} catch (IllegalStateException e) {
				logger.warn("No se ha podido borrar el jugador id={}: {}", id, e.getMessage());
				bloque.errores.add(new JugadorResumen(id, nombre, motivo(e)));
			} catch (Exception e) {
				logger.error("Error al borrar el jugador id={}: {}", id, e.getMessage(), e);
				bloque.errores.add(new JugadorResumen(id, nombre, "No se ha podido eliminar el jugador."));
			}
		}
		logger.debug("Fin borrarMultiple: borrados={}, errores={}", bloque.correctos.size(), bloque.errores.size());
		return ResponseEntity.ok(bloque.resumen());
	}

	/** Da de baja a cada jugador con la misma regla que la baja individual; los que ya lo estan se saltan. */
	@PostMapping("/jugadores/baja-multiple")
	@ResponseBody
	public ResponseEntity<?> darDeBajaMultiple(@RequestBody IdsRequest peticion) {
		List<Long> ids = idsValidos(peticion == null ? null : peticion.ids());
		if (ids == null) {
			return idsNoValidos();
		}
		logger.debug("Inicio darDeBajaMultiple: total={}", ids.size());
		Bloque bloque = new Bloque();
		for (Long id : ids) {
			String nombre = nombreDe(id);
			if (nombre == null) {
				bloque.errores.add(new JugadorResumen(id, "Jugador " + id, "el jugador no existe"));
				continue;
			}
			try {
				jugadoresService.darDeBaja(id);
				bloque.correctos.add(new JugadorResumen(id, nombre, null));
			} catch (IllegalStateException e) {
				// Ya estaba de baja: no es un error
				bloque.saltados.add(new JugadorResumen(id, nombre, motivo(e)));
			} catch (IllegalArgumentException e) {
				bloque.errores.add(new JugadorResumen(id, nombre, motivo(e)));
			} catch (Exception e) {
				logger.error("Error al dar de baja al jugador id={}: {}", id, e.getMessage(), e);
				bloque.errores.add(new JugadorResumen(id, nombre, "No se ha podido dar de baja."));
			}
		}
		logger.debug("Fin darDeBajaMultiple: ok={}, saltados={}, errores={}", bloque.correctos.size(),
				bloque.saltados.size(), bloque.errores.size());
		return ResponseEntity.ok(bloque.resumen());
	}

	/** Cambia de equipo a cada jugador con lo mismo que el guardado de su ficha ({@link JugadorService#cambiarEquipo}). */
	@PostMapping("/jugadores/cambiar-equipo-multiple")
	@ResponseBody
	public ResponseEntity<?> cambiarEquipoMultiple(@RequestBody CambiarEquipoRequest peticion) {
		List<Long> ids = idsValidos(peticion == null ? null : peticion.ids());
		if (ids == null) {
			return idsNoValidos();
		}
		EquipoDTO equipo = null;
		if (peticion.equipoId() != null) {
			try {
				equipo = equiposService.obtenerEquipoPorId(peticion.equipoId());
			} catch (EmptyResultDataAccessException e) {
				equipo = null;
			}
		}
		if (equipo == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "El equipo seleccionado no existe."));
		}
		logger.debug("Inicio cambiarEquipoMultiple: total={}, equipoId={}", ids.size(), equipo.getId());
		Bloque bloque = new Bloque();
		for (Long id : ids) {
			String nombre = nombreDe(id);
			if (nombre == null) {
				bloque.errores.add(new JugadorResumen(id, "Jugador " + id, "el jugador no existe"));
				continue;
			}
			try {
				CambioEquipoJugador cambio = jugadoresService.cambiarEquipo(id, equipo.getId());
				switch (cambio.resultado()) {
				case MOVIDO -> bloque.correctos.add(new JugadorResumen(id, nombre, null));
				case MOVIDO_SIN_DORSAL -> bloque.conAviso.add(new JugadorResumen(id, nombre, cambio.detalle()));
				case SALTADO -> bloque.saltados.add(new JugadorResumen(id, nombre, cambio.detalle()));
				}
			} catch (DorsalRepetidoException | IllegalArgumentException e) {
				bloque.errores.add(new JugadorResumen(id, nombre, motivo(e)));
			} catch (IllegalStateException e) {
				// De baja: no se mueve, hay que reactivarlo
				bloque.saltados.add(new JugadorResumen(id, nombre, motivo(e)));
			} catch (Exception e) {
				logger.error("Error al cambiar de equipo al jugador id={}: {}", id, e.getMessage(), e);
				bloque.errores.add(new JugadorResumen(id, nombre, "No se ha podido cambiar de equipo."));
			}
		}
		logger.debug("Fin cambiarEquipoMultiple: movidos={}, sinDorsal={}, saltados={}, errores={}",
				bloque.correctos.size(), bloque.conAviso.size(), bloque.saltados.size(), bloque.errores.size());
		return ResponseEntity.ok(bloque.resumen());
	}
}
