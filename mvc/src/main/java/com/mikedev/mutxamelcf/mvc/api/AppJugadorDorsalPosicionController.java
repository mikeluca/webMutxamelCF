package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.service.JugadorDorsalPosicionService;

/** Edición del dorsal y la posición de un jugador: entrenador de su equipo, COORDINADOR y ADMIN_APP. */
@RestController
@RequestMapping("/api/app/jugadores")
public class AppJugadorDorsalPosicionController {

    private final JugadorDorsalPosicionService service;

    public AppJugadorDorsalPosicionController(JugadorDorsalPosicionService service) {
        this.service = service;
    }

    /**
     * PUT /api/app/jugadores/{jugadorId}/dorsal-posicion {"dorsal": 7, "posicion": "DEFENSA"}
     *
     * Los dos campos se envían siempre (null = vacío). 200 con el jugador actualizado; 400 si falta un campo, el dorsal
     * no es un entero entre 1 y 99 o la posición no es de la lista; 401, 403 sin permiso sobre el equipo, 404 si el
     * jugador no existe o está de baja y 409 con el mensaje "El dorsal 7 ya lo tiene Nombre Apellido".
     */
    @PutMapping("/{jugadorId}/dorsal-posicion")
    public ResponseEntity<?> actualizar(
            @PathVariable Long jugadorId,
            @RequestBody(required = false) Map<String, Object> cuerpo,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            if (cuerpo == null || !cuerpo.containsKey("dorsal") || !cuerpo.containsKey("posicion")) {
                return ResponseEntity.badRequest().body("Hay que enviar dorsal y posicion (null para dejarlos vacíos)");
            }

            return ResponseEntity.ok(service.actualizar(
                    usuarioId, jugadorId, dorsalDe(cuerpo.get("dorsal")), posicionDe(cuerpo.get("posicion"))));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (NoSuchElementException e) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (DorsalRepetidoException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al guardar el dorsal y la posición");
        }
    }

    /** null, o un número entero (7 o 7.0): un texto o un decimal es un 400. */
    private static Integer dorsalDe(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof Number numero && numero.doubleValue() == Math.rint(numero.doubleValue())
                && Math.abs(numero.doubleValue()) < Integer.MAX_VALUE) {
            return numero.intValue();
        }

        throw new IllegalArgumentException("El dorsal debe ser un número entero entre 1 y 99");
    }

    private static String posicionDe(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof String texto) {
            return texto;
        }

        throw new IllegalArgumentException("La posición debe ser un código de la lista o null");
    }
}
