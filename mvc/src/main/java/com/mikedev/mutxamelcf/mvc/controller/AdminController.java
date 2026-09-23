package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;

/**
 * Panel de administración: pantalla de entrada (con el resumen de
 * jugadores/equipos/entrenadores) y logout. La gestión de cada
 * dominio (equipos, jugadores, cuerpo técnico, noticias, resultados)
 * vive en su propio controlador (EquipoAdminController,
 * JugadorAdminController, CuerpoTecnicoAdminController,
 * NoticiaAdminController, ResultadoAdminController).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	private final JugadorService jugadoresService;

	private final CuerpoTecnicoService cuerpoTecnicoService;

	private final EquipoService equiposService;

	public AdminController(JugadorService jugadoresService, CuerpoTecnicoService cuerpoTecnicoService,
			EquipoService equiposService) {
		this.jugadoresService = jugadoresService;
		this.cuerpoTecnicoService = cuerpoTecnicoService;
		this.equiposService = equiposService;
	}

	@GetMapping
	public String dashboardPrincipal(Authentication authentication) {
		logger.debug("Inicio dashboardPrincipal: autenticado={}", authentication != null);
		String destino = authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_SUPER".equals(authority.getAuthority()))
						? "redirect:/admin/pagos"
						: "redirect:/admin/admin";
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

	// Cuenta entrenadores distintos por nombre completo, evitando duplicados si
	// aparecen en varias categorias
	private long contarEntrenadoresUnicos(List<CuerpoTecnicoDTO> cuerpoTecnico) {
		return cuerpoTecnico.stream()
				.map(entrenador -> (entrenador.getNombre() + " " + entrenador.getApellidos()).trim())
				.distinct()
				.count();
	}

	// Construye el resumen de equipos/jugadores/entrenadores agrupados por
	// categoria, ordenados segun EquipoDTO
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
						Collectors.mapping(
								entrenador -> (entrenador.getNombre() + " " + entrenador.getApellidos()).trim(),
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
}
