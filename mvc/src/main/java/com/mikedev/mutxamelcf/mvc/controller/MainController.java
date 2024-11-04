package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.model.UsuarioDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NoticiaService;
import com.mikedev.mutxamelcf.service.ResultadoService;
import com.mikedev.mutxamelcf.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class MainController {

	@Autowired
	private JavaMailSender emailSender;

	@Autowired
	private JugadorService jugadoresService;

	@Autowired
	private CuerpoTecnicoService cuerpoTecnicoService;

	@Autowired
	private NoticiaService noticiaService;

	@Autowired
	private UsuarioService userService;

	@Autowired
	private ResultadoService resultadoService;

	@Autowired
	private EquipoService equipoService;

	@GetMapping("/login")
	public String login(@RequestParam(required = false) String error, Model model) {
		if (error != null) {
			model.addAttribute("errorMessage", "Usuario o contraseña incorrectos");
		}
		return "login";
	}

	@PostMapping("/login")
	public String authenticateUser(HttpServletRequest request, HttpServletResponse response, Model model) {
		String username = request.getParameter("username");
		String password = request.getParameter("password");

		UsuarioDTO usuario = userService.validarUsuario(username, password);

		if (usuario != null) {
			Authentication auth = new UsernamePasswordAuthenticationToken(username, password);
			SecurityContextHolder.getContext().setAuthentication(auth);
			return "redirect:/admin/admin"; // Redirigir a la página protegida
		} else {
			return "redirect:/login";
		}
	}

	@GetMapping("/")
	public String pantallaCarga() {
		// Sirve la pantalla splash como la primera vista
		return "pantalla-carga";
	}

	@GetMapping("/index")
	public String inicio(Model model) {
		// Lista de noticias
		List<NoticiaDTO> noticias = noticiaService.obtenerNoticiasParaMostrar();

		// Logos de patrocinadores
		List<String> patrocinadores = Arrays.asList("patrocinador1.jpg", "patrocinador2.jpg", "patrocinador3.jpg",
				"patrocinador4.jpg", "patrocinador5.jpg", "patrocinador6.jpg", "patrocinador1.jpg",
				"patrocinador2.jpg");

		model.addAttribute("noticias", noticias);
		model.addAttribute("patrocinadores", patrocinadores);
		return "index";
	}

	@GetMapping("/historia")
	public String historia(Model model) {
		return "historia";
	}

	@GetMapping("/contacto")
	public String contacto(Model model) {
		return "contacto";
	}

	// Método para obtener la lista de resultados y mostrarlos en una página HTML
	@GetMapping("/resultados")
	public String mostrarResultados(Model model) {
		List<ResultadoDTO> resultadosFutbol = resultadoService.obtenerResultados("F");
		List<ResultadoDTO> resultadosFutbolSala = resultadoService.obtenerResultados("FS");
		model.addAttribute("resultadosFutbol", resultadosFutbol);
		model.addAttribute("resultadosFutbolSala", resultadosFutbolSala);
		return "resultados";
	}

	@GetMapping("/categorias/{equipo}")
	public String categorias(@PathVariable String equipo, Model model) {
		// Lista de jugadores
		List<JugadorDTO> jugadores = jugadoresService.obtenerJugadoresPorEquipo(equipo);

		// Cuerpo técnico
		List<CuerpoTecnicoDTO> staff = cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo(equipo);

		model.addAttribute("jugadores", jugadores);
		model.addAttribute("staff", staff);
		model.addAttribute("categoria", equipo);
		return "plantilla";
	}

	@GetMapping("/ampliarNoticia/{id}")
	public String ampliarNoticia(@PathVariable int id, Model model) {
		NoticiaDTO noticia = noticiaService.obtenerNoticiaPorId(id);
		model.addAttribute("noticia", noticia);
		return "noticia";
	}

	@PostMapping("/enviar-email")
	public String enviarEmail(@RequestParam String nombre, @RequestParam String email, @RequestParam String mensaje) {
		try {
			// Crear mensaje MIME
			jakarta.mail.internet.MimeMessage message = emailSender.createMimeMessage();
			// Helper para crear el mensaje
			MimeMessageHelper helper = new MimeMessageHelper(message, true);
			// Configurar remitente, destinatario, asunto y cuerpo
			helper.setFrom("miguel_89_11@hotmail.com");
			helper.setTo("miguel_89_11@gmail.com");
			helper.setSubject("Contacto desde la WEB de: " + nombre);
			helper.setText("De: " + nombre + "\nEmail: " + email + "\n\\nMensaje: " + mensaje);
			// Enviar el mensaje
			emailSender.send(message);
			System.out.println("Correo HTML enviado correctamente.");

		} catch (jakarta.mail.MessagingException e) {
			System.out.println("Error al enviar el correo HTML: " + e.getMessage());
			e.printStackTrace();
		}
		return "redirect:/"; // Redirigir a la página de inicio después de enviar
	}

	@GetMapping("/listaEquipos/{deporte}")
	public String mostrarEquiposPorCategoria(@PathVariable String deporte, Model model) {
		Map<String, List<EquipoDTO>> equiposPorCategoria = equipoService.obtenerEquiposAgrupadosPorCategoria(deporte);
		model.addAttribute("equiposPorCategoria", equiposPorCategoria);
		return "listaEquipos";
	}

}
