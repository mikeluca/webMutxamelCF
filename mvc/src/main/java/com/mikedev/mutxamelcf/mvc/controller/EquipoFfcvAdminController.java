package com.mikedev.mutxamelcf.mvc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.mikedev.mutxamelcf.service.EquipoFfcvService;
import com.mikedev.mutxamelcf.service.EquipoService;

/**
 * Relaciones competición FFCV + letra -> equipo del club, que usa el
 * importador de partidos. Se revisan al cambiar de temporada. Solo SUPER y
 * ADMIN (ver SecurityConfig).
 */
@Controller
@RequestMapping("/admin/equipos-ffcv")
public class EquipoFfcvAdminController {

    private static final Logger logger = LoggerFactory.getLogger(EquipoFfcvAdminController.class);

    private static final String VISTA = "admin/equipos-ffcv";
    private static final String REDIRECCION = "redirect:/admin/equipos-ffcv";

    private final EquipoFfcvService equipoFfcvService;
    private final EquipoService equipoService;

    public EquipoFfcvAdminController(EquipoFfcvService equipoFfcvService, EquipoService equipoService) {
        this.equipoFfcvService = equipoFfcvService;
        this.equipoService = equipoService;
    }

    @GetMapping
    public String listar(Model model) {
        logger.debug("Inicio listar");

        model.addAttribute("relaciones", equipoFfcvService.listar());
        model.addAttribute("equipos", equipoService.obtenerTodos());

        logger.debug("Fin listar");

        return VISTA;
    }

    @PostMapping("/crear")
    public String crear(@RequestParam String competicion, @RequestParam(required = false) String letra,
            @RequestParam Long equipoId) {

        logger.debug("Inicio crear: equipoId={}", equipoId);

        try {
            equipoFfcvService.crear(competicion, letra, equipoId);
        } catch (IllegalArgumentException e) {
            return conError(e.getMessage());
        } catch (Exception e) {
            logger.error("Error al crear la relación FFCV: {}", e.getMessage(), e);
            return conError("Error al crear la relación");
        }

        return REDIRECCION;
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id, @RequestParam String competicion,
            @RequestParam(required = false) String letra, @RequestParam Long equipoId) {

        logger.debug("Inicio actualizar: id={}", id);

        try {
            equipoFfcvService.actualizar(id, competicion, letra, equipoId);
        } catch (IllegalArgumentException e) {
            return conError(e.getMessage());
        } catch (Exception e) {
            logger.error("Error al actualizar la relación FFCV: {}", e.getMessage(), e);
            return conError("Error al actualizar la relación");
        }

        return REDIRECCION;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {

        logger.debug("Inicio eliminar: id={}", id);

        try {
            equipoFfcvService.eliminar(id);
        } catch (IllegalArgumentException e) {
            return conError(e.getMessage());
        } catch (Exception e) {
            logger.error("Error al eliminar la relación FFCV: {}", e.getMessage(), e);
            return conError("Error al eliminar la relación");
        }

        return REDIRECCION;
    }

    private static String conError(String mensaje) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/equipos-ffcv")
                .queryParam("error", mensaje)
                .build().encode().toUriString();
    }

}
