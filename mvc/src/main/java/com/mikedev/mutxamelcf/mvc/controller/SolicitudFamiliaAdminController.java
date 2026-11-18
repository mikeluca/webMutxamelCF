package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Coincidencia;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;

/**
 * Pantalla "Solicitudes de familia" del panel (/admin/solicitudes-familia): las solicitudes pendientes con sus
 * coincidencias, aprobar (eligiendo el jugador), rechazar con motivo y el histórico de los últimos 7 días. Solo SUPER y
 * ADMIN (SecurityConfig); revisan todas las solicitudes. Cada acción redirige a la lista con un mensaje.
 */
@Controller
@RequestMapping("/admin/solicitudes-familia")
public class SolicitudFamiliaAdminController {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudFamiliaAdminController.class);

    private static final String VISTA = "admin/solicitudes-familia";
    private static final String REDIRECCION = "redirect:/admin/solicitudes-familia";

    private final SolicitudFamiliaService solicitudFamiliaService;

    public SolicitudFamiliaAdminController(SolicitudFamiliaService solicitudFamiliaService) {
        this.solicitudFamiliaService = solicitudFamiliaService;
    }

    @GetMapping
    public String ver(Authentication authentication, Model model) {

        Revisor revisor = Revisor.panel(authentication.getName());

        model.addAttribute("pendientes", solicitudFamiliaService.pendientes(revisor));
        model.addAttribute("historico", solicitudFamiliaService.historico(revisor));

        return VISTA;
    }

    /** Búsqueda de jugadores por nombre para elegir uno que no esté entre las coincidencias. */
    @GetMapping(value = "/jugadores", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Coincidencia> jugadores(@RequestParam(name = "q", required = false) String q,
            Authentication authentication) {

        return solicitudFamiliaService.buscarJugadores(Revisor.panel(authentication.getName()), q);
    }

    @PostMapping("/hijos/{hijoId}/aprobar")
    public String aprobar(@PathVariable long hijoId, @RequestParam(name = "jugadorId", required = false) Long jugadorId,
            Authentication authentication, RedirectAttributes redirect) {

        if (jugadorId == null) {
            return conError(redirect, "Elige el jugador al que se vincula");
        }

        try {

            solicitudFamiliaService.aprobarHijo(Revisor.panel(authentication.getName()), hijoId, jugadorId);

            return conMensaje(redirect, "Acceso aprobado: la cuenta ya ve a ese jugador");

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {

            return conError(redirect, e.getMessage());

        } catch (RuntimeException e) {

            logger.error("Error al aprobar el hijo {} de una solicitud de familia: {}", hijoId, e.getMessage(), e);
            return conError(redirect, "No se ha podido aprobar la solicitud");
        }
    }

    @PostMapping("/hijos/{hijoId}/rechazar")
    public String rechazar(@PathVariable long hijoId, @RequestParam(name = "motivo", required = false) String motivo,
            Authentication authentication, RedirectAttributes redirect) {

        try {

            solicitudFamiliaService.rechazarHijo(Revisor.panel(authentication.getName()), hijoId, motivo);

            return conMensaje(redirect, "Solicitud rechazada");

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {

            return conError(redirect, e.getMessage());

        } catch (RuntimeException e) {

            logger.error("Error al rechazar el hijo {} de una solicitud de familia: {}", hijoId, e.getMessage(), e);
            return conError(redirect, "No se ha podido rechazar la solicitud");
        }
    }

    private static String conMensaje(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("mensaje", mensaje);
        return REDIRECCION;
    }

    private static String conError(RedirectAttributes redirect, String error) {
        redirect.addFlashAttribute("error", error);
        return REDIRECCION;
    }
}
