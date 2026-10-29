package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.service.ContinuidadService;

/** Alta de jugadores de prueba desde la app: solo COORDINADOR y ADMIN_APP, con las mismas reglas que el asistente. */
@RestController
@RequestMapping("/api/app/pruebas")
public class AppPruebasController {

    public record AltaPruebaRequest(String nombre, String apellidos, Integer anioNacimiento) {
    }

    private final ContinuidadService continuidadService;

    public AppPruebasController(ContinuidadService continuidadService) {
        this.continuidadService = continuidadService;
    }

    /**
     * POST /api/app/pruebas {nombre, apellidos, anioNacimiento}
     *
     * Crea al jugador y su fila PRUEBA en la temporada siguiente, en la categoría que le toca por edad y sin equipo.
     */
    @PostMapping
    public ResponseEntity<?> alta(@RequestBody AltaPruebaRequest request, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            JugadorTemporada fila = continuidadService.altaPrueba(Long.valueOf(authentication.getName()),
                    request == null ? null : request.nombre(), request == null ? null : request.apellidos(),
                    request == null ? null : request.anioNacimiento());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("jugadorId", fila.getJugadorId());
            respuesta.put("categoria", fila.getCategoria());
            respuesta.put("temporadaId", fila.getTemporadaId());
            respuesta.put("estado", fila.getEstado());

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IllegalStateException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
