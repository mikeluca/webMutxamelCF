package com.mikedev.mutxamelcf.mvc.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoForm;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.NoticiaForm;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.ResultadoService;

@Controller
@RequestMapping("/admin") // Indica que todas las rutas dentro de esta clase comienzan con /admin
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	private JugadorService jugadoresService;

	@Autowired
	private FamiliarService familiarService;

	@Autowired
	private CuerpoTecnicoService cuerpoTecnicoService;

	@Autowired
	private NoticiaService noticiaService;

	@Autowired
	private ResultadoService resultadoService;

	@Autowired
	private EquipoService equiposService;

	// Logos de patrocinadores
	List<String> patrocinadores = Arrays.asList("patrocinador1.jpg", "patrocinador2.jpg", "patrocinador3.jpg",
			"patrocinador4.jpg", "patrocinador5.jpg", "patrocinador6.jpg", "patrocinador7.jpg", "patrocinador8.jpg");

	@GetMapping
	public String dashboardPrincipal(org.springframework.security.core.Authentication authentication) {
		logger.debug("Inicio dashboardPrincipal: autenticado={}", authentication != null);
		String destino = authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_SUPER".equals(authority.getAuthority()))
				? "redirect:/admin/pagos" : "redirect:/admin/admin";
		logger.debug("Fin dashboardPrincipal: destino={}", destino);
		return destino;
	}

	@GetMapping("/admin")
	public String login(Model model) {
		logger.debug("Inicio login");
		List<JugadorDTO> jugadores = jugadoresService.obtenerTodos();
		List<EquipoDTO> equipos = equiposService.obtenerTodos();
		List<CuerpoTecnicoDTO> cuerpoTecnico = cuerpoTecnicoService.obtenerTodos();

		long totalEntrenadores = contarEntrenadoresUnicos(cuerpoTecnico);
		List<Map<String, Object>> resumenCategorias = construirResumenCategorias(jugadores, equipos, cuerpoTecnico);

		model.addAttribute("totalJugadores", jugadores.size());
		model.addAttribute("totalEquipos", equipos.size());
		model.addAttribute("totalEntrenadores", totalEntrenadores);
		model.addAttribute("resumenCategorias", resumenCategorias);
		logger.debug("Fin login: jugadores={}, equipos={}, entrenadores={}, categorias={}", jugadores.size(),
				equipos.size(), totalEntrenadores, resumenCategorias.size());
		return "admin/admin";
	}

	// Cuenta entrenadores distintos por nombre completo, evitando duplicados si aparecen en varias categorias
	private long contarEntrenadoresUnicos(List<CuerpoTecnicoDTO> cuerpoTecnico) {
		return cuerpoTecnico.stream()
				.map(entrenador -> (entrenador.getNombre() + " " + entrenador.getApellidos()).trim())
				.distinct()
				.count();
	}

	// Construye el resumen de equipos/jugadores/entrenadores agrupados por categoria, ordenados segun EquipoDTO
	private List<Map<String, Object>> construirResumenCategorias(List<JugadorDTO> jugadores, List<EquipoDTO> equipos,
			List<CuerpoTecnicoDTO> cuerpoTecnico) {
		Map<String, String> ordenPorCategoria = equipos.stream()
				.collect(Collectors.toMap(EquipoDTO::getCategoria, EquipoDTO::getOrden, (orden, siguiente) -> orden));
		Map<String, Long> equiposPorCategoria = equipos.stream()
				.collect(Collectors.groupingBy(EquipoDTO::getCategoria, Collectors.counting()));
		Map<String, Long> jugadoresPorCategoria = jugadores.stream()
				.collect(Collectors.groupingBy(JugadorDTO::getCategoria, Collectors.counting()));
		Map<String, Set<String>> entrenadoresPorCategoria = cuerpoTecnico.stream()
				.collect(Collectors.groupingBy(CuerpoTecnicoDTO::getCategoria, LinkedHashMap::new,
						Collectors.mapping(entrenador -> (entrenador.getNombre() + " " + entrenador.getApellidos()).trim(),
								Collectors.toSet())));

		Set<String> categorias = new LinkedHashSet<>();
		categorias.addAll(ordenPorCategoria.keySet());
		categorias.addAll(jugadoresPorCategoria.keySet());
		categorias.addAll(entrenadoresPorCategoria.keySet());

		return categorias.stream()
				.sorted(Comparator.comparing(ordenPorCategoria::get, Comparator.nullsLast(String::compareTo)))
				.map(categoria -> {
					Map<String, Object> resumen = new LinkedHashMap<>();
					resumen.put("categoria", categoria);
					resumen.put("equipos", equiposPorCategoria.getOrDefault(categoria, 0L));
					resumen.put("jugadores", jugadoresPorCategoria.getOrDefault(categoria, 0L));
					resumen.put("entrenadores", entrenadoresPorCategoria.getOrDefault(categoria, Set.of()).size());
					return resumen;
				})
				.collect(Collectors.toList());
	}

	@GetMapping("/logout")
	public String logout() {
		logger.debug("Inicio logout");
		logger.debug("Fin logout");
		return "index";
	}

	// Método para listar todos los equipos
	@GetMapping("/equipos")
	public String listarEquipos(@RequestParam(required = false) String categoria, Model model) {
		logger.debug("Inicio listarEquipos: categoria={}", categoria);
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<String> categorias = equiposService.obtenerCategorias(); // Obtener categorías de equipos
		List<EquipoDTO> equipos;

		// Filtrar equipos por categoría si se proporciona
		if (categoria != null && !categoria.isEmpty()) {
			equipos = equiposService.obtenerTodosPorCategoria(categoria);
		} else {
			equipos = equiposService.obtenerTodos();
		}

		model.addAttribute("categorias", categorias);
		model.addAttribute("equipos", equipos);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin listarEquipos: total={}", equipos.size());
		return "admin/equipos"; // Retornar la vista para listar equipos
	}

	@PostMapping("/equipos/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarEquipo(@RequestParam String categoria,
			@RequestParam String nombre, @RequestParam String grupo, @RequestParam String deporte) {
		logger.debug("Inicio guardarEquipo: categoria={}, nombre={}, grupo={}, deporte={}", categoria, nombre, grupo,
				deporte);
		Map<String, String> response = new HashMap<>();
		try {
			EquipoDTO equipo = new EquipoDTO();
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
	public String borrarEquipo(@PathVariable Long id) {
		logger.debug("Inicio borrarEquipo: id={}", id);
		try {
			equiposService.eliminarEquipo(id); // Eliminar el equipo de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el equipo: {}", e.getMessage(), e); // Registrar el error
		}
		logger.debug("Fin borrarEquipo: id={}", id);
		return "redirect:/admin/equipos"; // Redirigir a la lista de equipos
	}

	// Método para listar jugadores
	@GetMapping("/jugadores")
	public String listarJugadores(@RequestParam(required = false) String categoria, Model model) {
		logger.debug("Inicio listarJugadores: categoria={}", categoria);
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<JugadorDTO> jugadores;

		// Filtrar jugadores por categoría si se proporciona
		if (categoria != null && !categoria.isEmpty()) {
			jugadores = jugadoresService.obtenerJugadoresPorCategoria(categoria);
		} else {
			jugadores = jugadoresService.obtenerTodos();
		}

		List<String> categorias = equiposService.obtenerCategorias();
		List<FamiliarDTO> listaFamiliares = familiarService.obtenerTodos();
		model.addAttribute("categorias", categorias);
		model.addAttribute("jugadores", jugadores);
		model.addAttribute("listaFamiliares", listaFamiliares);

		model.addAttribute("patrocinadores", patrocinadores);

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

			// Si hay una foto cargada, convertirla a byte[]
			if (!jugadorForm.getFoto().isEmpty()) {
				jugador.setFoto(jugadorForm.getFoto().getBytes());
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

	// Método para borrar un jugador por su DNI
	@PostMapping("/jugadores/borrar/{id}")
	public String borrarJugador(@PathVariable Long id) {
		logger.debug("Inicio borrarJugador: id={}", id);
		try {
			jugadoresService.eliminarJugador(id); // Eliminar el jugador de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el jugador: {}", e.getMessage(), e); // Registrar el error
		}
		logger.debug("Fin borrarJugador: id={}", id);
		return "redirect:/admin/jugadores"; // Redirigir a la lista de jugadores
	}

	// Método para listar el cuerpo técnico
	@GetMapping("/cuerpo-tecnico")
	public String listarCuerpoTecnico(@RequestParam(required = false) String categoria, Model model) {
		logger.debug("Inicio listarCuerpoTecnico: categoria={}", categoria);
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<CuerpoTecnicoDTO> cuerpoTecnico;

		// Filtrar cuerpo técnico por categoría si se proporciona
		if (categoria != null && !categoria.isEmpty()) {
			cuerpoTecnico = cuerpoTecnicoService.obtenerCuerpoTecnicoPorCategoria(categoria);
		} else {
			cuerpoTecnico = cuerpoTecnicoService.obtenerTodos();
		}

		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("cuerpoTecnico", cuerpoTecnico);

		model.addAttribute("patrocinadores", patrocinadores);

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

			// Si hay una foto cargada, convertirla a byte[]
			if (!cuerpoTecnicoForm.getFoto().isEmpty()) {
				staff.setFoto(cuerpoTecnicoForm.getFoto().getBytes());
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

	// Método para borrar un miembro del cuerpo técnico por su DNI
	@PostMapping("/cuerpo-tecnico/borrar/{id}")
	public String borrarCuerpoTecnico(@PathVariable Long id) {
		logger.debug("Inicio borrarCuerpoTecnico: id={}", id);
		try {
			cuerpoTecnicoService.eliminarCuerpoTecnico(id); // Eliminar el cuerpo técnico de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el cuerpo técnico: {}", e.getMessage(), e); // Registrar el error
		}
		logger.debug("Fin borrarCuerpoTecnico: id={}", id);
		return "redirect:/admin/cuerpo-tecnico"; // Redirigir a la lista del cuerpo técnico
	}

	// Método para listar las noticias
	@GetMapping("/noticias")
	public String listarNoticias(Model model) {
		logger.debug("Inicio listarNoticias");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<NoticiaDTO> listaNoticias = noticiaService.obtenerTodas(); // Obtener todas las noticias
		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("listaNoticias", listaNoticias);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin listarNoticias: total={}", listaNoticias.size());
		return "admin/noticias"; // Retornar la vista para listar noticias
	}

	// Método para guardar una nueva noticia
	@PostMapping("/noticias/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarNoticia(@ModelAttribute NoticiaForm noticiaForm) {
		logger.debug("Inicio guardarNoticia: titulo={}", noticiaForm.getTitulo());
		Map<String, String> response = new HashMap<>();
		try {
			NoticiaDTO noticia = new NoticiaDTO();
			noticia.setTitulo(noticiaForm.getTitulo());

			// Convierte el texto antes de guardarlo poniéndole saltos de línea
			String contenidoConSaltos = HtmlUtils.htmlEscape(noticiaForm.getContenido()).replaceAll("\n", "<br>");
			noticia.setContenido(contenidoConSaltos);
			noticia.setFecha(new Date()); // Establecer la fecha actual

			if (!noticiaForm.getImagen().isEmpty()) {
				long maxSize = 2 * 1024 * 1024; // 2 MB (puedes ajustar el límite)
				if (noticiaForm.getImagen().getSize() > maxSize) {
					logger.warn("Imagen de noticia demasiado grande: tamano={} bytes", noticiaForm.getImagen().getSize());
					response.put("error", "La imagen excede el tamaño máximo permitido (2 MB).");
					logger.debug("Fin guardarNoticia: resultado=IMAGEN_DEMASIADO_GRANDE");
					return ResponseEntity.badRequest().body(response);
				}
				noticia.setImagen(noticiaForm.getImagen().getBytes());
			}

			if (noticiaService.guardarNoticia(noticia)) {
				response.put("mensaje", "Noticia generada correctamente.");
				logger.debug("Fin guardarNoticia: resultado=OK");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta la noticia.");
				logger.debug("Fin guardarNoticia: resultado=FALLIDO");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar la noticia: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar el equipo");
			logger.debug("Fin guardarNoticia: resultado=ERROR");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar una noticia por su ID
	@PostMapping("/noticias/borrar/{id}")
	public String borrarNoticia(@PathVariable int id) {
		logger.debug("Inicio borrarNoticia: id={}", id);
		try {
			noticiaService.eliminarNoticia(id); // Eliminar la noticia de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar la noticia: {}", e.getMessage(), e); // Registrar el error
		}
		logger.debug("Fin borrarNoticia: id={}", id);
		return "redirect:/admin/noticias"; // Redirigir a la lista de noticias
	}

	// Método para listar los resultados del calendario
	@GetMapping("/calendario-resultados")
	public String listarResultados(Model model) {
		logger.debug("Inicio listarResultados");
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);

		// Obtener todos los resultados desde la base de datos
		List<ResultadoDTO> resultadosFutbol = resultadoService.obtenerResultados("F");
		List<ResultadoDTO> resultadosFutbolSala = resultadoService.obtenerResultados("FS");
		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("resultadosFutbol", resultadosFutbol);
		model.addAttribute("resultadosFutbolSala", resultadosFutbolSala);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin listarResultados: futbol={}, futbolSala={}", resultadosFutbol.size(),
				resultadosFutbolSala.size());
		return "admin/calendario-resultados"; // Retornar la vista para listar resultados
	}

	// Método para actualizar el resultado de un equipo
	@PostMapping("/calendario-resultados/actualizar/{equipo}")
	public String actualizarResultado(@PathVariable String equipo, @RequestParam String categoria,
			@RequestParam String resultado, @RequestParam String rival, @RequestParam String dia,
			@RequestParam String hora, @RequestParam String campo) {
		logger.debug("Inicio actualizarResultado: equipo={}, categoria={}, rival={}", equipo, categoria, rival);
		ResultadoDTO resultadoDTO = new ResultadoDTO();
		resultadoDTO.setCategoria(categoria);
		resultadoDTO.setEquipo(equipo);
		resultadoDTO.setResultado(resultado);
		resultadoDTO.setRival(rival);

		// Parsear la fecha desde el formato "dd/MM/yyyy"
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
		try {
			if (dia != null && !dia.isEmpty()) {
				Date date = formatter.parse(dia);
				resultadoDTO.setDia(date);
			} else {
				resultadoDTO.setDia(null);
			}

		} catch (ParseException e) {
			logger.error("Error al parsear la fecha del resultado: {}", e.getMessage(), e); // Registrar error de parseo
			return "redirect:/admin/calendario-resultados?error=Fecha inválida"; // Redirigir con mensaje de error
		}

		resultadoDTO.setHora(hora);
		resultadoDTO.setCampo(campo);

		try {
			resultadoService.actualizarResultado(resultadoDTO); // Actualizar el resultado en la base de datos
		} catch (Exception e) {
			logger.error("Error al actualizar el resultado: {}", e.getMessage(), e); // Registrar el error
			return "redirect:/admin/calendario-resultados?error=Error al actualizar el resultado"; // Redirigir con
																									// mensaje de error
		}

		logger.debug("Fin actualizarResultado: equipo={}", equipo);
		return "redirect:/admin/calendario-resultados"; // Redirigir a la lista de resultados
	}
}
