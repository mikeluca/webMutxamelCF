package com.mikedev.mutxamelcf.mvc.controller;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.service.JuegoService;

/**
 * Pantalla "Juego Mutxigol" (/admin/juego-mutxigol) de Administración de la App: los perfiles del juego con su cuenta
 * (nombre y email, solo aquí), cambiar un alias, ocultar o mostrar en el ranking y borrar una puntuación o todas las
 * de un perfil. Solo SUPER y ADMIN (SecurityConfig), como el resto de "Administración de la App". Cada acción redirige
 * a la lista con un mensaje.
 */
@Controller
@RequestMapping("/admin/juego-mutxigol")
public class AdminJuegoMutxigolController {

    private static final Logger logger = LoggerFactory.getLogger(AdminJuegoMutxigolController.class);

    private static final String VISTA = "admin/juego-mutxigol";
    private static final String REDIRECCION = "redirect:/admin/juego-mutxigol";

    private final JuegoService juegoService;

    public AdminJuegoMutxigolController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    @GetMapping
    public String ver(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "usuario", required = false) Long usuario,
            Model model) {

        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("perfiles", juegoService.listarPerfilesAdmin(q));

        if (usuario != null) {
            try {
                model.addAttribute("seleccionado", juegoService.obtenerPerfilAdmin(usuario));
                model.addAttribute("puntuaciones", juegoService.puntuacionesAdmin(usuario));
            } catch (NoSuchElementException e) {
                model.addAttribute("error", e.getMessage());
            }
        }

        return VISTA;
    }

    @PostMapping("/{usuarioId}/alias")
    public String cambiarAlias(
            @PathVariable long usuarioId,
            @RequestParam(name = "alias", required = false) String alias,
            @RequestParam(name = "q", required = false) String q,
            RedirectAttributes redirect) {

        return accion(redirect, q, usuarioId, "Alias cambiado.", () -> juegoService.cambiarAliasAdmin(usuarioId, alias));
    }

    @PostMapping("/{usuarioId}/oculto")
    public String ocultar(
            @PathVariable long usuarioId,
            @RequestParam(name = "oculto") boolean oculto,
            @RequestParam(name = "q", required = false) String q,
            RedirectAttributes redirect) {

        return accion(redirect, q, usuarioId,
                oculto ? "Perfil oculto del ranking." : "Perfil visible otra vez en el ranking.",
                () -> juegoService.ocultarAdmin(usuarioId, oculto));
    }

    @PostMapping("/puntuaciones/{puntuacionId}/borrar")
    public String borrarPuntuacion(
            @PathVariable long puntuacionId,
            @RequestParam(name = "usuario") long usuarioId,
            @RequestParam(name = "q", required = false) String q,
            RedirectAttributes redirect) {

        return accion(redirect, q, usuarioId, "Puntuación borrada.", () -> juegoService.borrarPuntuacionAdmin(puntuacionId));
    }

    @PostMapping("/{usuarioId}/puntuaciones/borrar-todas")
    public String borrarTodas(
            @PathVariable long usuarioId,
            @RequestParam(name = "q", required = false) String q,
            RedirectAttributes redirect) {

        return accion(redirect, q, usuarioId, "Puntuaciones borradas.", () -> juegoService.borrarPuntuacionesAdmin(usuarioId));
    }

    private String accion(RedirectAttributes redirect, String q, long usuarioId, String mensaje, Runnable accion) {

        try {
            accion.run();
            redirect.addFlashAttribute("mensaje", mensaje);

        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            // Alias no válido o repetido, perfil que ya no existe...: se enseña el motivo tal cual.
            redirect.addFlashAttribute("error", e.getMessage());

        } catch (RuntimeException e) {
            logger.error("Error moderando el juego de Mutxigol", e);
            redirect.addFlashAttribute("error", "No se ha podido completar la acción");
        }

        if (q != null && !q.isBlank()) {
            redirect.addAttribute("q", q);
        }

        redirect.addAttribute("usuario", usuarioId);

        return REDIRECCION;
    }
}
