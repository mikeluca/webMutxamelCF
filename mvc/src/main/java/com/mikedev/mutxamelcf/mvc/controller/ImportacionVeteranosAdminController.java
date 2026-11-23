package com.mikedev.mutxamelcf.mvc.controller;

import java.io.IOException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import com.mikedev.mutxamelcf.model.PrevisualizacionVeteranos;
import com.mikedev.mutxamelcf.model.ResultadoImportacionVeteranos;
import com.mikedev.mutxamelcf.service.ImportacionVeteranosService;

/**
 * Importación de la clasificación de la Liga Veteranos Ciudad de Alicante (LVCA): un bloque aparte en la pantalla de
 * importación de clasificaciones. Solo SUPER y ADMIN (/admin/ffcv/** en SecurityConfig), igual que la importación de la
 * FFCV. El fichero es la página https://ligaveteranosalicante.es/scoretable/ guardada desde el navegador ("Guardar
 * como… → Página web"): la sube siempre una persona, el servidor nunca la descarga.
 *
 * <p>
 * Como en las clasificaciones de la FFCV, la vista previa se guarda en la sesión y es la que se confirma: el servidor
 * nunca vuelve a leer otro archivo ni acepta datos del navegador.
 */
@Controller
@RequestMapping("/admin/ffcv/veteranos")
public class ImportacionVeteranosAdminController {

    private static final Logger logger = LoggerFactory.getLogger(ImportacionVeteranosAdminController.class);

    public static final String SESION_PREVIA = "importacionVeteranosPrevia";

    private static final String REDIRECCION = "redirect:/admin/ffcv/clasificaciones";

    /** La página guardada pesa unos 60 KB; esto solo descarta lo absurdo. */
    public static final long TAMANO_MAXIMO_BYTES = 3L * 1024 * 1024;

    private final ImportacionVeteranosService importacionService;

    public ImportacionVeteranosAdminController(ImportacionVeteranosService importacionService) {
        this.importacionService = importacionService;
    }

    /** Valida el nombre y el tamaño del fichero; devuelve el mensaje de error o null si vale. Lo comparte la app. */
    public static String validarArchivo(MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            return "Selecciona la página guardada de la clasificación de la Liga Veteranos";
        }

        String nombre = archivo.getOriginalFilename();
        String minusculas = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);

        if (!minusculas.endsWith(".html") && !minusculas.endsWith(".htm")) {
            return "El archivo debe ser la página guardada (.html): en el navegador, Guardar como… → Página web";
        }

        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            return "El archivo es demasiado grande para ser la clasificación de la Liga Veteranos";
        }

        return null;
    }

    @PostMapping("/previsualizar")
    public String previsualizar(
            @RequestParam("archivo") MultipartFile archivo,
            HttpSession sesion,
            RedirectAttributes redirect) {

        logger.debug("Inicio previsualizar veteranos");

        // Una vista previa nueva sustituye siempre a la anterior.
        sesion.removeAttribute(SESION_PREVIA);

        String invalido = validarArchivo(archivo);

        if (invalido != null) {
            return conError(redirect, invalido);
        }

        try {

            PrevisualizacionVeteranos previa = importacionService.previsualizar(archivo.getBytes());

            sesion.setAttribute(SESION_PREVIA, previa);

            return REDIRECCION;

        } catch (IllegalArgumentException e) {

            return conError(redirect, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el archivo subido: {}", e.getMessage());
            return conError(redirect, "No se pudo leer el archivo");

        } catch (Exception e) {

            logger.error("Error al previsualizar la clasificación de veteranos: {}", e.getMessage(), e);
            return conError(redirect, "Error al procesar el archivo");
        }
    }

    @PostMapping("/confirmar")
    public String confirmar(HttpSession sesion, Authentication authentication, RedirectAttributes redirect) {

        logger.debug("Inicio confirmar veteranos");

        PrevisualizacionVeteranos previa = (PrevisualizacionVeteranos) sesion.getAttribute(SESION_PREVIA);

        if (previa == null) {
            return conError(redirect, "La vista previa ha caducado: vuelve a subir el archivo");
        }

        try {

            String usuario = authentication != null ? authentication.getName() : "desconocido";

            ResultadoImportacionVeteranos resultado = importacionService.confirmar(usuario, previa);

            // Importada: no se puede confirmar dos veces la misma vista previa.
            sesion.removeAttribute(SESION_PREVIA);
            redirect.addFlashAttribute("resultadoVeteranos", resultado);

            return REDIRECCION;

        } catch (IllegalArgumentException e) {

            return conError(redirect, e.getMessage());

        } catch (Exception e) {

            logger.error("Error al confirmar la clasificación de veteranos: {}", e.getMessage(), e);
            return conError(redirect, "Error al guardar la importación");
        }
    }

    @PostMapping("/cancelar")
    public String cancelar(HttpSession sesion) {
        sesion.removeAttribute(SESION_PREVIA);
        return REDIRECCION;
    }

    private static String conError(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("errorVeteranos", mensaje);
        return REDIRECCION;
    }
}
