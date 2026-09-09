package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.service.ConvocatoriaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/convocatorias")
public class AppConvocatoriaController {

        private final ConvocatoriaService convocatoriaService;

        public AppConvocatoriaController(
                        ConvocatoriaService convocatoriaService) {

                this.convocatoriaService = convocatoriaService;
        }

        /**
         * Crear una convocatoria.
         *
         * POST /api/app/convocatorias
         */
        @PostMapping
        public ResponseEntity<?> crear(
                        @RequestBody ConvocatoriaGuardarRequest request,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        ConvocatoriaResponse convocatoria = convocatoriaService.crear(
                                        usuarioId,
                                        request);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(convocatoria);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al crear la convocatoria");
                }
        }

        /**
         * Actualizar una convocatoria.
         *
         * PUT /api/app/convocatorias/{id}
         */
        @PutMapping("/{id}")
        public ResponseEntity<?> actualizar(
                        @PathVariable Long id,
                        @RequestBody ConvocatoriaGuardarRequest request,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        ConvocatoriaResponse convocatoria = convocatoriaService.actualizar(
                                        usuarioId,
                                        id,
                                        request);

                        return ResponseEntity.ok(convocatoria);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al actualizar la convocatoria");
                }
        }

        /**
         * Obtener una convocatoria concreta.
         *
         * GET /api/app/convocatorias/{id}
         */
        @GetMapping("/{id}")
        public ResponseEntity<?> obtenerPorId(
                        @PathVariable Long id,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        ConvocatoriaResponse convocatoria = convocatoriaService.obtenerPorId(
                                        usuarioId,
                                        id);

                        return ResponseEntity.ok(convocatoria);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

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
                                        .body("Error al obtener la convocatoria");
                }
        }

        /**
         * Obtener convocatorias de un equipo.
         *
         * GET /api/app/convocatorias?equipoId=...
         */
        @GetMapping
        public ResponseEntity<?> obtenerPorEquipo(
                        @RequestParam(required = false) Long equipoId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                if (equipoId == null) {

                        return ResponseEntity
                                        .badRequest()
                                        .body("El parámetro equipoId es obligatorio");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        List<ConvocatoriaResponse> convocatorias = convocatoriaService.obtenerPorEquipo(
                                        usuarioId,
                                        equipoId);

                        return ResponseEntity.ok(convocatorias);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (IllegalArgumentException e) {

                        return ResponseEntity
                                        .badRequest()
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener las convocatorias");
                }
        }

        /**
         * Eliminar una convocatoria.
         *
         * DELETE /api/app/convocatorias/{id}
         */
        @DeleteMapping("/{id}")
        public ResponseEntity<?> eliminar(
                        @PathVariable Long id,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.valueOf(
                                        authentication.getName());

                        convocatoriaService.eliminar(
                                        usuarioId,
                                        id);

                        return ResponseEntity.noContent().build();

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no válido");

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
                                        .body("Error al eliminar la convocatoria");
                }
        }
}