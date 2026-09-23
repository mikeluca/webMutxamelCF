package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Date;
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
import org.springframework.web.util.HtmlUtils;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.NoticiaForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

@Controller
@RequestMapping("/admin")
public class NoticiaAdminController {

	private static final Logger logger = LoggerFactory.getLogger(NoticiaAdminController.class);

	private final NoticiaService noticiaService;

	private final EquipoService equiposService;

	private final NotificacionAppService notificacionAppService;

	public NoticiaAdminController(NoticiaService noticiaService, EquipoService equiposService,
			NotificacionAppService notificacionAppService) {
		this.noticiaService = noticiaService;
		this.equiposService = equiposService;
		this.notificacionAppService = notificacionAppService;
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

		model.addAttribute("patrocinadores", AdminViewSupport.PATROCINADORES);

		logger.debug("Fin listarNoticias: total={}", listaNoticias.size());
		return "admin/noticias"; // Retornar la vista para listar noticias
	}

	// Método para crear o editar una noticia
	@PostMapping("/noticias/guardar")
	@ResponseBody
	public ResponseEntity<Map<String, String>> guardarNoticia(@ModelAttribute NoticiaForm noticiaForm) {
		logger.debug("Inicio guardarNoticia: id={}, titulo={}", noticiaForm.getId(), noticiaForm.getTitulo());
		Map<String, String> response = new HashMap<>();
		try {
			NoticiaDTO noticiaExistente = null;

			if (noticiaForm.getId() != null) {
				noticiaExistente = noticiaService.obtenerNoticiaPorId(noticiaForm.getId().intValue());
				if (noticiaExistente == null) {
					logger.warn("Noticia inexistente al editar: id={}", noticiaForm.getId());
					response.put("error", "La noticia que intentas editar ya no existe.");
					logger.debug("Fin guardarNoticia: resultado=NO_ENCONTRADA");
					return ResponseEntity.badRequest().body(response);
				}
			}

			NoticiaDTO noticia = new NoticiaDTO();
			if (noticiaExistente != null) {
				noticia.setId(noticiaForm.getId().intValue());
			}
			noticia.setTitulo(HtmlUtils.htmlEscape(noticiaForm.getTitulo()));

			// Convierte el texto antes de guardarlo poniéndole saltos de línea
			String contenidoConSaltos = HtmlUtils.htmlEscape(noticiaForm.getContenido()).replaceAll("\n", "<br>");
			noticia.setContenido(contenidoConSaltos);
			// Al editar se conserva la fecha original; al crear, la fecha actual
			noticia.setFecha(noticiaExistente != null ? noticiaExistente.getFecha() : new Date());

			if (!noticiaForm.getImagen().isEmpty()) {
				long maxSize = 2 * 1024 * 1024; // 2 MB (puedes ajustar el límite)
				String errorImagen = AdminViewSupport.validarImagen(noticiaForm.getImagen(), maxSize);
				if (errorImagen != null) {
					logger.warn("Imagen de noticia invalida: titulo={}, motivo={}", noticiaForm.getTitulo(), errorImagen);
					response.put("error", errorImagen);
					logger.debug("Fin guardarNoticia: resultado=IMAGEN_INVALIDA");
					return ResponseEntity.badRequest().body(response);
				}
				noticia.setImagen(noticiaForm.getImagen().getBytes());
			} else if (noticiaExistente != null) {
				// Sin imagen nueva al editar: se conserva la imagen ya guardada
				noticia.setImagen(noticiaService.obtenerImagenNoticia(noticiaExistente.getId()));
			}

			if (noticiaService.guardarNoticia(noticia)) {
				response.put("mensaje", noticiaExistente != null ? "Noticia actualizada correctamente."
						: "Noticia generada correctamente.");

				/*
				 * Solo avisamos a la app cuando la noticia es nueva, no en
				 * cada edición posterior.
				 */
				if (noticiaExistente == null) {
					notificacionAppService.difundirATodos(
							"NOTICIA",
							"📰 Nueva noticia",
							noticia.getTitulo(),
							(long) noticia.getId());
				}

				logger.debug("Fin guardarNoticia: resultado=OK");
				return ResponseEntity.ok(response);
			} else {
				response.put("error", "Error guardando la noticia.");
				logger.debug("Fin guardarNoticia: resultado=FALLIDO");
				return ResponseEntity.badRequest().body(response);
			}
		} catch (Exception e) {
			logger.error("Error al guardar la noticia: {}", e.getMessage(), e); // Registrar el error
			response.put("error", "Error al guardar la noticia");
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
}
