package com.mikedev.mutxamelcf.mvc.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.model.ClubRivalFila;
import com.mikedev.mutxamelcf.model.ResumenGeneracionClubes;
import com.mikedev.mutxamelcf.serviceimpl.EscudoImagen;
import com.mikedev.mutxamelcf.serviceimpl.NombreClub;
import com.mikedev.mutxamelcf.service.ClubRivalService;

/**
 * Pantalla "Escudos de rivales" (/admin/escudos-rivales): un registro por club rival, con su escudo. Solo SUPER y ADMIN
 * (SecurityConfig). Cada acción redirige a la lista con un mensaje, como el resto de pantallas de importación.
 */
@Controller
@RequestMapping("/admin/escudos-rivales")
public class EscudoRivalAdminController {

    private static final Logger logger = LoggerFactory.getLogger(EscudoRivalAdminController.class);

    private static final String VISTA = "admin/escudos-rivales";
    private static final String REDIRECCION = "redirect:/admin/escudos-rivales";

    // "Nombre del club · #12": lo que escribe o elige el usuario al unir (el id va al final).
    private static final Pattern DESTINO_CON_ID = Pattern.compile("#(\\d+)\\s*$");

    private final ClubRivalService clubRivalService;

    public EscudoRivalAdminController(ClubRivalService clubRivalService) {
        this.clubRivalService = clubRivalService;
    }

    @GetMapping
    public String ver(
            @RequestParam(name = "filtro", defaultValue = "sin") String filtro,
            @RequestParam(name = "q", required = false) String q,
            Model model) {

        List<ClubRivalFila> todos = clubRivalService.listar();
        int[] contador = clubRivalService.contarConEscudo();

        boolean soloSinEscudo = !"todos".equals(filtro);
        String buscado = q == null ? "" : NombreClub.normalizar(q);

        // Los que no tienen escudo primero (es lo que hay que hacer); dentro de cada grupo, por nombre.
        List<ClubRivalFila> filtrados = todos.stream()
                .filter(c -> !soloSinEscudo || !c.tieneEscudo())
                .filter(c -> buscado.isEmpty() || coincide(c, buscado, q))
                .sorted(java.util.Comparator.comparing(ClubRivalFila::tieneEscudo))
                .toList();

        model.addAttribute("clubes", filtrados);
        model.addAttribute("todos", todos);
        model.addAttribute("conEscudo", contador[0]);
        model.addAttribute("total", contador[1]);
        model.addAttribute("filtro", soloSinEscudo ? "sin" : "todos");
        model.addAttribute("q", q == null ? "" : q);

        return VISTA;
    }

    private static boolean coincide(ClubRivalFila club, String buscadoNormalizado, String buscado) {

        return NombreClub.normalizar(club.nombre()).contains(buscadoNormalizado)
                || club.nombre().toLowerCase(Locale.ROOT).contains(buscado.trim().toLowerCase(Locale.ROOT))
                || club.alias().stream().anyMatch(a -> a.contains(buscadoNormalizado));
    }

    @PostMapping("/generar")
    public String generar(RedirectAttributes redirect) {

        try {

            ResumenGeneracionClubes resumen = clubRivalService.generar();

            redirect.addFlashAttribute("resumen", resumen);

        } catch (Exception e) {

            logger.error("Error al generar los clubes rivales: {}", e.getMessage(), e);
            redirect.addFlashAttribute("error", "No se han podido generar los clubes");
        }

        return REDIRECCION;
    }

    @PostMapping("/{id}/escudo")
    public String subirEscudo(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            RedirectAttributes redirect) {

        if (archivo == null || archivo.isEmpty()) {
            return conError(redirect, "Selecciona la imagen del escudo (PNG o JPG)");
        }

        if (archivo.getSize() > EscudoImagen.MAXIMO_BYTES) {
            return conError(redirect, "El escudo pesa más de 2 MB");
        }

        try {

            clubRivalService.subirEscudo(id, archivo.getBytes());

            return conMensaje(redirect, "Escudo guardado");

        } catch (IllegalArgumentException e) {

            return conError(redirect, e.getMessage());

        } catch (IOException e) {

            logger.error("No se pudo leer el escudo subido: {}", e.getMessage());
            return conError(redirect, "No se pudo leer el archivo");
        }
    }

    @PostMapping("/{id}/quitar-escudo")
    public String quitarEscudo(@PathVariable Long id, RedirectAttributes redirect) {

        try {
            clubRivalService.quitarEscudo(id);
            return conMensaje(redirect, "Escudo quitado");
        } catch (IllegalArgumentException e) {
            return conError(redirect, e.getMessage());
        }
    }

    @PostMapping("/{id}/renombrar")
    public String renombrar(@PathVariable Long id, @RequestParam("nombre") String nombre, RedirectAttributes redirect) {

        try {
            clubRivalService.renombrar(id, nombre);
            return conMensaje(redirect, "Club renombrado");
        } catch (IllegalArgumentException e) {
            return conError(redirect, e.getMessage());
        }
    }

    @PostMapping("/{id}/unir")
    public String unir(@PathVariable Long id, @RequestParam("destino") String destino, RedirectAttributes redirect) {

        Long destinoId = resolverDestino(destino);

        if (destinoId == null) {
            return conError(redirect, "Elige el club destino de la lista");
        }

        try {
            clubRivalService.unir(id, destinoId);
            return conMensaje(redirect, "Clubes unidos: el club de origen se ha borrado");
        } catch (IllegalArgumentException e) {
            return conError(redirect, e.getMessage());
        }
    }

    @PostMapping("/{id}/borrar")
    public String borrar(@PathVariable Long id, RedirectAttributes redirect) {

        try {
            clubRivalService.borrar(id);
            return conMensaje(redirect, "Club borrado");
        } catch (IllegalArgumentException e) {
            return conError(redirect, e.getMessage());
        }
    }

    /** El id sale del "#id" final que lleva cada opción de la lista; si el usuario solo escribió el nombre, por nombre. */
    private Long resolverDestino(String destino) {

        if (destino == null || destino.isBlank()) {
            return null;
        }

        Matcher m = DESTINO_CON_ID.matcher(destino);

        if (m.find()) {
            return Long.valueOf(m.group(1));
        }

        String buscado = destino.trim();

        return clubRivalService.listar().stream()
                .filter(c -> c.nombre().equalsIgnoreCase(buscado))
                .map(ClubRivalFila::id)
                .findFirst()
                .orElse(null);
    }

    private static String conError(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("error", mensaje);
        return REDIRECCION;
    }

    private static String conMensaje(RedirectAttributes redirect, String mensaje) {
        redirect.addFlashAttribute("mensaje", mensaje);
        return REDIRECCION;
    }
}
