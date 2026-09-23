package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.ResultadoService;

@Controller
public class MainController {

	private static final Logger logger = LoggerFactory.getLogger(MainController.class);

	private final ComunicacionesService comunicacionesService;

	private final JugadorService jugadoresService;

	private final CuerpoTecnicoService cuerpoTecnicoService;

	private final NoticiaService noticiaService;

	private final ResultadoService resultadoService;

	private final EquipoService equipoService;

	public MainController(ComunicacionesService comunicacionesService, JugadorService jugadoresService,
			CuerpoTecnicoService cuerpoTecnicoService, NoticiaService noticiaService,
			ResultadoService resultadoService, EquipoService equipoService) {
		this.comunicacionesService = comunicacionesService;
		this.jugadoresService = jugadoresService;
		this.cuerpoTecnicoService = cuerpoTecnicoService;
		this.noticiaService = noticiaService;
		this.resultadoService = resultadoService;
		this.equipoService = equipoService;
	}

	// Logos de patrocinadores
	List<String> patrocinadores = Arrays.asList("patrocinador1.jpg", "patrocinador2.jpg", "patrocinador3.jpg",
			"patrocinador4.jpg", "patrocinador5.jpg", "patrocinador6.jpg", "patrocinador7.jpg", "patrocinador8.jpg");

	@GetMapping("/login")
	public String login(@RequestParam(required = false) String error, Model model) {
		logger.debug("Inicio login: error={}", error);
		if ("bloqueado".equals(error)) {
			model.addAttribute("errorMessage", "Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.");
		} else if (error != null) {
			model.addAttribute("errorMessage", "Usuario o contraseña incorrectos");
		}
		logger.debug("Fin login");
		return "login";
	}

	@GetMapping("/")
	public String pantallaCarga() {
		logger.debug("Inicio pantallaCarga");
		// Sirve la pantalla splash como la primera vista
		logger.debug("Fin pantallaCarga");
		return "pantalla-carga";
	}

	@GetMapping("/historia")
	public String historia(Model model) {
		logger.debug("Inicio historia");
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin historia");
		return "historia";
	}

	@GetMapping("/estadisticasPalmares")
	public String estadisticasPalmares(Model model) {
		logger.debug("Inicio estadisticasPalmares");
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin estadisticasPalmares");
		return "estadisticasPalmares";
	}

	@GetMapping("/obraSocial")
	public String obraSocial(Model model) {
		logger.debug("Inicio obraSocial");
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin obraSocial");
		return "obraSocial";
	}

	@GetMapping("/index")
	public String inicio(Model model) {
		logger.debug("Inicio inicio");
		// Lista de noticias
		List<NoticiaDTO> noticias = noticiaService.obtenerNoticiasParaMostrar();

		model.addAttribute("noticias", noticias);
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin inicio: noticias={}", noticias.size());
		return "index";
	}

	@GetMapping("/contacto")
	public String contacto(Model model) {
		logger.debug("Inicio contacto");
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin contacto");
		return "contacto";
	}

	@GetMapping("/tienda")
	public String tienda(Model model) {
		logger.debug("Inicio tienda");
		model.addAttribute("patrocinadores", patrocinadores);
		logger.debug("Fin tienda");
		return "tienda";
	}

	@PostMapping("/tienda/crear-pedido")
	public String crearPedido(@RequestParam String nombre,
			@RequestParam(required = false, defaultValue = "") String telefono,
			@RequestParam String email, @RequestParam(name = "prenda", required = false) List<String> prendas,
			@RequestParam(name = "cantidad", required = false) List<String> cantidades,
			@RequestParam(name = "talla", required = false) List<String> tallas) {
		logger.debug("Inicio crearPedido: nombre={}, email={}", nombre, email);
		if (!esPedidoValido(nombre, email, prendas, cantidades, tallas)) {
			logger.warn("Pedido invalido recibido: nombre={}, email={}", nombre, email);
			logger.debug("Fin crearPedido: resultado=INVALIDO");
			return "redirect:/tienda?error=true";
		}

		try {
			List<Integer> cantidadesValidadas = cantidades.stream().map(Integer::parseInt)
					.filter(cantidad -> cantidad > 0 && cantidad <= 20).collect(Collectors.toList());
			if (cantidadesValidadas.size() != cantidades.size()) {
				logger.warn("Cantidades fuera de rango en el pedido: nombre={}", nombre);
				logger.debug("Fin crearPedido: resultado=CANTIDAD_INVALIDA");
				return "redirect:/tienda?error=true";
			}

			String textoPedido = construirTextoPedido(nombre, telefono, email, prendas, cantidadesValidadas, tallas);

			if (!comunicacionesService.enviarPedidoTienda(nombre, email, textoPedido)) {
				logger.warn("No se pudo enviar el pedido por email: nombre={}", nombre);
				logger.debug("Fin crearPedido: resultado=ERROR_ENVIO");
				return "redirect:/tienda?error=true";
			}
			logger.info("Pedido enviado por email correctamente: nombre={}", nombre);
			logger.debug("Fin crearPedido: resultado=OK");
			return "redirect:/tienda?pedido=ok";
		} catch (NumberFormatException exception) {
			logger.error("Error al procesar el pedido: {}", exception.getMessage(), exception);
			logger.debug("Fin crearPedido: resultado=ERROR");
			return "redirect:/tienda?error=true";
		}
	}

	// Valida que el pedido tenga los datos obligatorios y que las prendas/tallas
	// sean opciones permitidas
	private boolean esPedidoValido(String nombre, String email, List<String> prendas, List<String> cantidades,
			List<String> tallas) {
		Set<String> prendasValidas = Set.of("Camiseta oficial", "Segunda equipacion - colaboracion AECC");
		Set<String> tallasValidas = Set.of("2", "4", "6", "8", "10", "12", "14", "S", "M", "L", "XL", "XXL", "3XL",
				"4XL");

		if (nombre.isBlank() || email.isBlank() || prendas == null || cantidades == null || tallas == null
				|| prendas.size() != cantidades.size() || cantidades.size() != tallas.size() || prendas.isEmpty()) {
			return false;
		}
		if (!prendas.stream().allMatch(prendasValidas::contains)) {
			return false;
		}
		return tallas.stream().flatMap(talla -> Arrays.stream(talla.split(",\\s*"))).allMatch(tallasValidas::contains);
	}

	// Construye el texto del email de pedido a partir de los datos del formulario
	private String construirTextoPedido(String nombre, String telefono, String email, List<String> prendas,
			List<Integer> cantidadesValidadas, List<String> tallas) {
		StringBuilder pedido = new StringBuilder("Datos del cliente\nNombre: ").append(nombre)
				.append("\nTelefono: ").append(telefono).append("\nEmail: ").append(email)
				.append("\n\nPrendas seleccionadas\n");
		for (int i = 0; i < prendas.size(); i++) {
			pedido.append("- ").append(prendas.get(i)).append(" | Cantidad: ")
					.append(cantidadesValidadas.get(i)).append(" | Tallas: ").append(tallas.get(i)).append("\n");
		}
		return pedido.toString();
	}

	// Método para obtener la lista de resultados y mostrarlos en una página HTML
	@GetMapping("/resultados")
	public String mostrarResultados(Model model) {
		logger.debug("Inicio mostrarResultados");
		List<ResultadoDTO> resultadosFutbol = resultadoService.obtenerResultados("F");
		model.addAttribute("resultadosFutbol", resultadosFutbol);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin mostrarResultados: total={}", resultadosFutbol.size());
		return "resultados";
	}

	@GetMapping("/categorias/{equipo}")
	public String categorias(@PathVariable String equipo, Model model) {
		logger.debug("Inicio categorias: equipo={}", equipo);
		String pantalla;

		if (equipo.contains("Esc")) {
			// Las escuelitas tienen una foto en comun del equipo, no jugadores individuales
			List<JugadorDTO> jugadores = jugadoresService.obtenerJugadoresPorEquipo(equipo);
			if (jugadores != null && !jugadores.isEmpty()) {
				JugadorDTO equipoEscuelita = jugadores.get(0);
				model.addAttribute("equipoEscuelita", equipoEscuelita);
			} else {
				model.addAttribute("equipoEscuelita", new JugadorDTO());
			}

			pantalla = "plantillasEscuelas";
		} else {
			// Lista de jugadores
			List<JugadorDTO> jugadores = jugadoresService.obtenerJugadoresPorEquipo(equipo);
			model.addAttribute("jugadores", jugadores);
			// Cuerpo técnico
			List<CuerpoTecnicoDTO> staff = cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo(equipo);
			model.addAttribute("staff", staff);

			pantalla = "plantilla";
		}

		model.addAttribute("categoria", equipo);
		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin categorias: equipo={}, pantalla={}", equipo, pantalla);
		return pantalla;
	}

	@GetMapping("/ampliarNoticia/{id}")
	public String ampliarNoticia(@PathVariable int id, Model model) {
		logger.debug("Inicio ampliarNoticia: id={}", id);
		NoticiaDTO noticia = noticiaService.obtenerNoticiaPorId(id);
		model.addAttribute("noticia", noticia);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin ampliarNoticia: id={}, encontrada={}", id, noticia != null);
		return "noticia";
	}

	@PostMapping("/enviar-email")
	public String enviarEmail(@RequestParam String nombre, @RequestParam String email, @RequestParam String mensaje) {
		logger.debug("Inicio enviarEmail: nombre={}, email={}", nombre, email);
		if (comunicacionesService.enviarMensajeContacto(nombre, email, mensaje)) {
			logger.info("Correo de contacto enviado correctamente: nombre={}", nombre);
		} else {
			logger.warn("No se pudo enviar el correo de contacto: nombre={}", nombre);
		}
		logger.debug("Fin enviarEmail: nombre={}", nombre);
		return "redirect:/index"; // Redirigir a la página de inicio después de enviar
	}

	@GetMapping("/listaEquipos/{deporte}")
	public String mostrarEquiposPorCategoria(@PathVariable String deporte, Model model) {
		logger.debug("Inicio mostrarEquiposPorCategoria: deporte={}", deporte);
		Map<String, List<EquipoDTO>> equiposPorCategoria = equipoService.obtenerEquiposAgrupadosPorCategoria(deporte);
		model.addAttribute("equiposPorCategoria", equiposPorCategoria);

		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin mostrarEquiposPorCategoria: deporte={}, grupos={}", deporte, equiposPorCategoria.size());
		return "listaEquipos";
	}

	@GetMapping("/politicaPrivacidad")
	public String politicaPrivacidad(Model model) {
		logger.debug("Inicio politicaPrivacidad");
		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin politicaPrivacidad");
		return "politicaPrivacidad";
	}

	@GetMapping("/todasNoticias")
	public String todasNoticias(Model model) {
		logger.debug("Inicio todasNoticias");
		List<NoticiaDTO> todas = noticiaService.obtenerTodas();
		model.addAttribute("noticias", todas);
		model.addAttribute("patrocinadores", patrocinadores);

		logger.debug("Fin todasNoticias: total={}", todas.size());
		return "todasNoticias";
	}

}
