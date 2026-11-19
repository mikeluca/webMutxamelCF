package com.mikedev.mutxamelcf.mvc.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.model.CartelRedes;
import com.mikedev.mutxamelcf.service.FondosCartelesService;
import com.mikedev.mutxamelcf.service.FondosCartelesService.Fondo;

/**
 * Pantalla "Fondos de carteles" (/admin/fondos-carteles): el fondo de cada cartel para redes sociales (vista previa,
 * subir uno nuevo y restaurar el de por defecto). Solo SUPER y ADMIN (SecurityConfig). Cada acción redirige a la
 * pantalla con un mensaje, como "Escudos de rivales".
 */
@Controller
@RequestMapping("/admin/fondos-carteles")
public class FondosCartelesAdminController {

    private static final Logger logger = LoggerFactory.getLogger(FondosCartelesAdminController.class);

    private static final String VISTA = "admin/fondos-carteles";
    private static final String REDIRECCION = "redirect:/admin/fondos-carteles";

    private final FondosCartelesService fondosService;

    public FondosCartelesAdminController(FondosCartelesService fondosService) {
        this.fondosService = fondosService;
    }

    /** Una fila de la pantalla: un cartel con su fondo vigente. */
    public record FilaCartel(String codigo, String nombre, boolean disponible, boolean personalizado, Integer ancho,
            Integer alto, String tipoContenido, LocalDateTime fecha, String usuario) {
    }

    @GetMapping
    public String ver(Model model) {

        List<FilaCartel> filas = new ArrayList<>();

        for (CartelRedes cartel : CartelRedes.values()) {

            Fondo fondo = cartel.disponible() ? fondosService.actual(cartel) : null;

            filas.add(fondo == null
                    ? new FilaCartel(cartel.name(), cartel.nombre(), false, false, null, null, null, null, null)
                    : new FilaCartel(cartel.name(), cartel.nombre(), true, fondo.personalizado(), fondo.ancho(),
                            fondo.alto(), fondo.tipoContenido(), fondo.fecha(), fondo.usuario()));
        }

        model.addAttribute("carteles", filas);
        model.addAttribute("maximoMb", FondosCartelesService.MAXIMO_BYTES / (1024 * 1024));
        model.addAttribute("anchoMinimo", FondosCartelesService.ANCHO_MINIMO);
        model.addAttribute("altoMinimo", FondosCartelesService.ALTO_MINIMO);

        return VISTA;
    }

    /** La imagen del fondo vigente, para la vista previa. */
    @GetMapping("/{cartel}/imagen")
    public ResponseEntity<byte[]> imagen(@PathVariable String cartel) {

        CartelRedes elegido = cartelODefecto(cartel);
        Fondo fondo = elegido == null || !elegido.disponible() ? null : fondosService.actual(elegido);

        if (fondo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fondo.tipoContenido()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(fondo.bytes());
    }

    @PostMapping("/{cartel}/subir")
    public String subir(@PathVariable String cartel, @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication, RedirectAttributes redirect) {

        CartelRedes elegido = cartelODefecto(cartel);

        if (elegido == null) {
            return conError(redirect, "El cartel no existe");
        }

        if (archivo == null || archivo.isEmpty()) {
            return conError(redirect, "Selecciona la imagen del fondo (JPG o PNG)");
        }

        if (archivo.getSize() > FondosCartelesService.MAXIMO_BYTES) {
            return conError(redirect, "La imagen pesa más de 5 MB");
        }

        try {

            fondosService.subir(elegido, archivo.getBytes(), authentication == null ? null : authentication.getName());

            return conMensaje(redirect, "Fondo de " + elegido.nombre() + " guardado");

        } catch (IllegalArgumentException e) {

            return conError(redirect, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el fondo subido: {}", e.getMessage());
            return conError(redirect, "No se pudo leer el archivo");

        } catch (DataAccessException e) {

            logger.error("Error de base de datos al guardar el fondo del cartel {}: {}", cartel, e.getMessage(), e);
            return conError(redirect, "No se ha podido guardar el fondo: error de base de datos");
        }
    }

    @PostMapping("/{cartel}/restaurar")
    public String restaurar(@PathVariable String cartel, RedirectAttributes redirect) {

        CartelRedes elegido = cartelODefecto(cartel);

        if (elegido == null) {
            return conError(redirect, "El cartel no existe");
        }

        try {

            boolean habia = fondosService.restaurar(elegido);

            return conMensaje(redirect, habia ? "Vuelve el fondo por defecto de " + elegido.nombre()
                    : elegido.nombre() + " ya usaba el fondo por defecto");

        } catch (DataAccessException e) {

            logger.error("Error de base de datos al restaurar el fondo del cartel {}: {}", cartel, e.getMessage(), e);
            return conError(redirect, "No se ha podido restaurar el fondo: error de base de datos");
        }
    }

    private static CartelRedes cartelODefecto(String codigo) {

        try {
            return CartelRedes.de(codigo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String conError(RedirectAttributes redirect, String error) {
        redirect.addFlashAttribute("error", error);
        return REDIRECCION;
    }

    private static String conMensaje(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("mensaje", mensaje);
        return REDIRECCION;
    }
}
