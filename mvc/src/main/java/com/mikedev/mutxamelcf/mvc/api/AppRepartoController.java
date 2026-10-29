package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.DevolucionRepartoRequest;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.service.RepartoService;

/**
 * Reparto de septiembre desde la app: solo COORDINADOR y ADMIN_APP (lo comprueba el servicio). Listar por categoría,
 * asignar jugadores a un equipo definitivo y crear un equipo nuevo de la categoría.
 */
@RestController
@RequestMapping("/api/app/reparto")
public class AppRepartoController {

    private final RepartoService repartoService;

    public AppRepartoController(RepartoService repartoService) {
        this.repartoService = repartoService;
    }

    /** GET /api/app/reparto/categorias: las categorías formativas con equipos en la temporada activa. */
    @GetMapping("/categorias")
    public ResponseEntity<?> categorias(Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> {
            repartoService.comprobarCoordinador(usuario);
            return repartoService.categorias();
        });
    }

    /**
     * GET /api/app/reparto?categoria=Alevín
     *
     * A un lado los jugadores sin equipo definitivo (pretemporada y pruebas) y, al otro, los equipos definitivos de la
     * categoría con sus jugadores. 400 si no es una categoría formativa.
     */
    @GetMapping
    public ResponseEntity<?> obtenerCategoria(@RequestParam String categoria, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> {
            repartoService.comprobarCoordinador(usuario);
            return repartoService.obtenerCategoria(categoria);
        });
    }

    /**
     * POST /api/app/reparto/asignar {equipoId, jugadores: [{jugadorId, dorsal}], confirmarPruebas}
     *
     * Asigna jugadores a un equipo definitivo. 409 con {@code codigo: PRUEBAS_SIN_CONFIRMAR} si hay jugadores de
     * prueba y no se ha enviado {@code confirmarPruebas: true} (no se asigna nada). Los dorsales repetidos no
     * impiden asignar: salen en {@code avisos}.
     */
    @PostMapping("/asignar")
    public ResponseEntity<?> asignar(@RequestBody AsignacionRepartoRequest request, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> {
            repartoService.comprobarCoordinador(usuario);
            return repartoService.asignar(request);
        });
    }

    /**
     * POST /api/app/reparto/devolver {jugadorIds: [..]}
     *
     * Devuelve a la pretemporada de su categoría a jugadores asignados por error (EQUIPO_DEFINITIVO = 0, sin dorsal). El
     * equipo definitivo que se queda vacío no se desactiva solo.
     */
    @PostMapping("/devolver")
    public ResponseEntity<?> devolver(@RequestBody DevolucionRepartoRequest request, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> {
            repartoService.comprobarCoordinador(usuario);
            return repartoService.devolverAPretemporada(request);
        });
    }

    /** POST /api/app/reparto/equipos?categoria=Alevín: un equipo nuevo con la siguiente letra, inactivo hasta su primer jugador. */
    @PostMapping("/equipos")
    public ResponseEntity<?> crearEquipo(@RequestParam String categoria, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.CREATED, usuario -> {
            repartoService.comprobarCoordinador(usuario);
            return repartoService.crearEquipo(categoria);
        });
    }

    private ResponseEntity<?> ejecutar(Authentication authentication, HttpStatus exito,
            Function<Long, Object> accion) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.status(exito).body(accion.apply(Long.valueOf(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (PruebasSinConfirmarException e) {

            Map<String, Object> conflicto = new LinkedHashMap<>();
            conflicto.put("codigo", "PRUEBAS_SIN_CONFIRMAR");
            conflicto.put("mensaje", e.getMessage());
            conflicto.put("pruebas", e.getPruebas());

            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicto);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IllegalStateException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
