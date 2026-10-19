package com.mikedev.mutxamelcf.mvc.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;
import com.mikedev.mutxamelcf.service.ImportacionClasificacionesService;

/**
 * Importación semanal de clasificaciones desde el ListadoClasificaciones.xlsx
 * de la intranet de la FFCV. Solo SUPER y ADMIN (/admin/ffcv/** en
 * SecurityConfig). Pantalla con tres pasos: subir, revisar la vista previa y
 * confirmar. La vista previa se guarda en la sesión y es la que se confirma:
 * el servidor nunca vuelve a leer otro archivo ni acepta datos del navegador.
 */
@Controller
@RequestMapping("/admin/ffcv/clasificaciones")
public class ImportacionClasificacionesAdminController {

    private static final Logger logger = LoggerFactory.getLogger(ImportacionClasificacionesAdminController.class);

    static final String SESION_PREVIA = "importacionClasificacionesPrevia";

    private static final String VISTA = "admin/clasificaciones-ffcv";
    private static final String REDIRECCION = "redirect:/admin/ffcv/clasificaciones";

    /** El archivo real pesa unos 500 KB (el límite de multipart es 5 MB); esto solo descarta lo absurdo. */
    static final long TAMANO_MAXIMO_BYTES = 3L * 1024 * 1024;

    private final ImportacionClasificacionesService importacionService;

    public ImportacionClasificacionesAdminController(ImportacionClasificacionesService importacionService) {
        this.importacionService = importacionService;
    }

    @GetMapping
    public String ver(HttpSession sesion, Model model) {

        PrevisualizacionClasificaciones previa = (PrevisualizacionClasificaciones) sesion.getAttribute(SESION_PREVIA);

        model.addAttribute("previa", previa);

        return VISTA;
    }

    @PostMapping("/previsualizar")
    public String previsualizar(
            @RequestParam("archivo") MultipartFile archivo,
            HttpSession sesion,
            RedirectAttributes redirect) {

        logger.debug("Inicio previsualizar");

        // Una vista previa nueva sustituye siempre a la anterior.
        sesion.removeAttribute(SESION_PREVIA);

        if (archivo == null || archivo.isEmpty()) {
            return conError(redirect, "Selecciona el archivo ListadoClasificaciones.xlsx");
        }

        String nombre = archivo.getOriginalFilename();

        if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return conError(redirect, "El archivo debe ser un Excel .xlsx (ListadoClasificaciones.xlsx)");
        }

        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            return conError(redirect, "El archivo es demasiado grande para ser un listado de clasificaciones");
        }

        try {

            PrevisualizacionClasificaciones previa = importacionService.previsualizar(archivo.getBytes());

            sesion.setAttribute(SESION_PREVIA, previa);

            logger.debug("Fin previsualizar: equipos={}", previa.equipos().size());

            return REDIRECCION;

        } catch (IllegalArgumentException e) {

            return conError(redirect, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return conError(redirect, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la importación de clasificaciones: {}", e.getMessage(), e);
            return conError(redirect, "Error al procesar el archivo");
        }
    }

    @PostMapping("/confirmar")
    public String confirmar(
            @RequestParam(name = "equipoId", required = false) List<Long> equipoIds,
            HttpSession sesion,
            Authentication authentication,
            RedirectAttributes redirect) {

        logger.debug("Inicio confirmar");

        PrevisualizacionClasificaciones previa = (PrevisualizacionClasificaciones) sesion.getAttribute(SESION_PREVIA);

        if (previa == null) {
            return conError(redirect, "La vista previa ha caducado: vuelve a subir el archivo");
        }

        try {

            String usuario = authentication != null ? authentication.getName() : "desconocido";

            ResultadoImportacionClasificaciones resultado = importacionService.confirmar(usuario, previa, equipoIds);

            // Importada: no se puede confirmar dos veces la misma vista previa.
            sesion.removeAttribute(SESION_PREVIA);
            redirect.addFlashAttribute("resultado", resultado);

            logger.debug("Fin confirmar");

            return REDIRECCION;

        } catch (IllegalArgumentException e) {

            // La vista previa se conserva para poder corregir la selección.
            return conError(redirect, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la importación de clasificaciones: {}", e.getMessage(), e);
            return conError(redirect, "Error al guardar la importación");
        }
    }

    @PostMapping("/cancelar")
    public String cancelar(HttpSession sesion) {
        sesion.removeAttribute(SESION_PREVIA);
        return REDIRECCION;
    }

    private static String conError(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return REDIRECCION;
    }

}
