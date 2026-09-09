package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import com.mikedev.mutxamelcf.service.EntrenamientoService;
import java.util.List;

@RestController
@RequestMapping("/api/app/entrenamientos")
public class AppEntrenamientoController {

        private final EntrenamientoService entrenamientoService;

        public AppEntrenamientoController(
                        EntrenamientoService entrenamientoService) {

                this.entrenamientoService = entrenamientoService;
        }

        /**
         * Crear un entrenamiento con la asistencia completa del equipo.
         *
         * POST /api/app/entrenamientos
         */
        @PostMapping
        public ResponseEntity<?> crear(
                        @RequestBody EntrenamientoGuardarRequest request,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(
                                        authentication.getName());

                        EntrenamientoResponse creado = entrenamientoService.crear(
                                        usuarioId,
                                        request);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(creado);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.BAD_REQUEST)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al crear el entrenamiento");
                }
        }

        @PutMapping("/{id}")
        public ResponseEntity<EntrenamientoResponse> actualizar(
                        @PathVariable Long id,
                        @RequestBody EntrenamientoGuardarRequest request) {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication == null
                                || !authentication.isAuthenticated()
                                || authentication.getPrincipal() == null) {

                        throw new SecurityException("Usuario no autenticado");
                }

                Long usuarioAppId = Long.valueOf(authentication.getName());

                EntrenamientoResponse response = entrenamientoService.actualizar(
                                usuarioAppId,
                                id,
                                request);

                return ResponseEntity.ok(response);
        }

        /**
         * Obtener un entrenamiento concreto.
         *
         * GET /api/app/entrenamientos/{equipoId}
         */
        @GetMapping("/{equipoId}")
        public ResponseEntity<?> obtenerPorId(
                        @PathVariable Long equipoId,
                        Authentication authentication) {

                if (equipoId == null) {
                        return ResponseEntity
                                        .badRequest()
                                        .body("El parámetro equipoId es obligatorio");
                }

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(
                                        authentication.getName());

                        EntrenamientoResponse entrenamiento = entrenamientoService.obtenerPorId(
                                        usuarioId,
                                        equipoId);

                        return ResponseEntity.ok(
                                        entrenamiento);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener el entrenamiento");
                }
        }

        /**
         * Obtener los entrenamientos de un equipo.
         *
         * GET /api/app/entrenamientos?equipoId=1
         */
        @GetMapping
        public ResponseEntity<?> obtenerPorEquipo(
                        @org.springframework.web.bind.annotation.RequestParam Long equipoId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(
                                        authentication.getName());

                        List<EntrenamientoResponse> entrenamientos = entrenamientoService.obtenerPorEquipo(
                                        usuarioId,
                                        equipoId);

                        return ResponseEntity.ok(
                                        entrenamientos);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener los entrenamientos");
                }
        }

}