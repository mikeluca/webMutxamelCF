package com.mikedev.mutxamelcf.mvc.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.NoticiaForm;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.ResultadoService;

@Controller
@RequestMapping("/admin") // Indica que todas las rutas dentro de esta clase comienzan con /admin
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class); // Logger ¡

	@Autowired
	private JugadorService jugadoresService;

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

	@GetMapping("/admin")
	public String login() {
		return "admin/admin";
	}

	@GetMapping("/logout")
	public String logout() {
		return "index";
	}

	// Método para listar todos los equipos
	@GetMapping("/equipos")
	public String listarEquipos(@RequestParam(required = false) String categoria, Model model) {
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

		return "admin/equipos"; // Retornar la vista para listar equipos
	}

	@PostMapping("/equipos/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarEquipo(@RequestParam String categoria,
			@RequestParam String nombre, @RequestParam String grupo, @RequestParam String deporte) {
		Map<String, String> response = new HashMap<>();
		try {
			EquipoDTO equipo = new EquipoDTO();
			equipo.setCategoria(categoria);
			equipo.setNombre(nombre);
			equipo.setGrupo(grupo);
			equipo.setDeporte(deporte);

			if (equiposService.guardar(equipo)) {
				response.put("mensaje", "Equipo guardado correctamente.");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error. El equipo ya existe.");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar el equipo: {}", e.getMessage(), e);
			response.put("error", "Error al guardar el equipo");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un equipo por su ID
	@PostMapping("/equipos/borrar/{id}")
	public String borrarEquipo(@PathVariable Long id) {
		try {
			equiposService.eliminarEquipo(id); // Eliminar el equipo de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el equipo: {}", e.getMessage(), e); // Registrar el error
		}
		return "redirect:/admin/equipos"; // Redirigir a la lista de equipos
	}

	// Método para listar jugadores
	@GetMapping("/jugadores")
	public String listarJugadores(@RequestParam(required = false) String categoria, Model model) {
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
		model.addAttribute("categorias", categorias);
		model.addAttribute("jugadores", jugadores);

		model.addAttribute("patrocinadores", patrocinadores);

		return "admin/jugadores"; // Retornar la vista para listar jugadores
	}

	// Método para guardar un nuevo jugador
	@PostMapping("/jugadores/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarJugador(@ModelAttribute JugadorForm jugadorForm) {
		Map<String, String> response = new HashMap<>();

		try {
			JugadorDTO jugador = new JugadorDTO();
			jugador.setNombre(jugadorForm.getNombre());
			jugador.setApellidos(jugadorForm.getApellidos());

			EquipoDTO e = equiposService.obtenerEquipoPorId(jugadorForm.getEquipo());

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
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta al jugador.");
				return ResponseEntity.badRequest().body(response);
			}

		} catch (Exception e) {
			logger.error("Error al guardar el jugador: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar el jugador");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un jugador por su DNI
	@PostMapping("/jugadores/borrar/{id}")
	public String borrarJugador(@PathVariable Long id) {
		try {
			jugadoresService.eliminarJugador(id); // Eliminar el jugador de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el jugador: {}", e.getMessage(), e); // Registrar el error
		}
		return "redirect:/admin/jugadores"; // Redirigir a la lista de jugadores
	}

	// Método para listar el cuerpo técnico
	@GetMapping("/cuerpo-tecnico")
	public String listarCuerpoTecnico(@RequestParam(required = false) String categoria, Model model) {
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

		return "admin/cuerpo-tecnico"; // Retornar la vista para listar el cuerpo técnico
	}

	// Método para guardar un nuevo miembro del cuerpo técnico
	@PostMapping("/cuerpo-tecnico/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarCuerpoTecnico(
			@ModelAttribute CuerpoTecnicoForm cuerpoTecnicoForm) {
		Map<String, String> response = new HashMap<>();
		try {
			CuerpoTecnicoDTO staff = new CuerpoTecnicoDTO();
			staff.setNombre(cuerpoTecnicoForm.getNombre());
			staff.setApellidos(cuerpoTecnicoForm.getApellidos());

			EquipoDTO e = equiposService.obtenerEquipoPorId(cuerpoTecnicoForm.getEquipo());

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
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta al cuerpo técnico.");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar el cuerpo técnico: {}", e.getMessage(), e);
			response.put("error", "Error al guardar el cuerpo técnico");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar un miembro del cuerpo técnico por su DNI
	@PostMapping("/cuerpo-tecnico/borrar/{id}")
	public String borrarCuerpoTecnico(@PathVariable Long id) {
		try {
			cuerpoTecnicoService.eliminarCuerpoTecnico(id); // Eliminar el cuerpo técnico de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar el cuerpo técnico: {}", e.getMessage(), e); // Registrar el error
		}
		return "redirect:/admin/cuerpo-tecnico"; // Redirigir a la lista del cuerpo técnico
	}

	// Método para listar las noticias
	@GetMapping("/noticias")
	public String listarNoticias(Model model) {
		List<EquipoDTO> listaEquipos = equiposService.obtenerTodos(); // Obtener todos los equipos
		model.addAttribute("listaEquipos", listaEquipos);
		List<NoticiaDTO> listaNoticias = noticiaService.obtenerTodas(); // Obtener todas las noticias
		List<String> categorias = equiposService.obtenerCategorias();
		model.addAttribute("categorias", categorias);
		model.addAttribute("listaNoticias", listaNoticias);

		model.addAttribute("patrocinadores", patrocinadores);

		return "admin/noticias"; // Retornar la vista para listar noticias
	}

	// Método para guardar una nueva noticia
	@PostMapping("/noticias/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarNoticia(@ModelAttribute NoticiaForm noticiaForm) {
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
					response.put("error", "La imagen excede el tamaño máximo permitido (2 MB).");
					return ResponseEntity.badRequest().body(response);
				}
				noticia.setImagen(noticiaForm.getImagen().getBytes());
			}

			if (noticiaService.guardarNoticia(noticia)) {
				response.put("mensaje", "Noticia generada correctamente.");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error dando de alta la noticia.");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar la noticia: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar el equipo");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	// Método para borrar una noticia por su ID
	@PostMapping("/noticias/borrar/{id}")
	public String borrarNoticia(@PathVariable int id) {
		try {
			noticiaService.eliminarNoticia(id); // Eliminar la noticia de la base de datos
		} catch (Exception e) {
			logger.error("Error al borrar la noticia: {}", e.getMessage(), e); // Registrar el error
		}
		return "redirect:/admin/noticias"; // Redirigir a la lista de noticias
	}

	// Método para listar los resultados del calendario
	@GetMapping("/calendario-resultados")
	public String listarResultados(Model model) {
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

		return "admin/calendario-resultados"; // Retornar la vista para listar resultados
	}

	// Método para actualizar el resultado de un equipo
	@PostMapping("/calendario-resultados/actualizar/{equipo}")
	public String actualizarResultado(@PathVariable String equipo, @RequestParam String categoria,
			@RequestParam String resultado, @RequestParam String rival, @RequestParam String dia,
			@RequestParam String hora, @RequestParam String campo) {
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

		return "redirect:/admin/calendario-resultados"; // Redirigir a la lista de resultados
	}
}
