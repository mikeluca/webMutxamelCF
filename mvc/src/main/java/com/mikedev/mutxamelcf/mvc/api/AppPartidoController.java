package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.service.PartidoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/partidos")
public class AppPartidoController {

        private final PartidoService partidoService;

        public AppPartidoController(
                        PartidoService partidoService) {

                this.partidoService = partidoService;
        }

        /**
         * Crear un partido para un equipo que el entrenador pueda
         * gestionar.
         *
         * POST /api/app/partidos
         */
        @PostMapping
        public ResponseEntity<?> crear(
                        @Valid @RequestBody PartidoGuardarRequest request,
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

                        PartidoDTO creado = partidoService.crear(
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
                                        .body("Error al crear el partido");
                }
        }

        /**
         * Actualizar un partido de un equipo que el entrenador pueda
         * gestionar.
         *
         * PUT /api/app/partidos/{id}
         */
        @PutMapping("/{id}")
        public ResponseEntity<?> actualizar(
                        @PathVariable Long id,
                        @Valid @RequestBody PartidoGuardarRequest request,
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

                        PartidoDTO actualizado = partidoService.actualizar(
                                        usuarioId,
                                        id,
                                        request);

                        return ResponseEntity.ok(
                                        actualizado);

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
                                        .body("Error al actualizar el partido");
                }
        }

        /**
         * Partidos de un equipo que todavía no tienen convocatoria
         * asociada, para el selector de "crear convocatoria a partir de
         * un partido existente" en la app.
         *
         * Si se indica incluirPartidoId, ese partido se incluye en el
         * listado aunque ya tenga convocatoria (para que, al editar una
         * convocatoria existente, su partido vinculado siga apareciendo
         * como opción seleccionada).
         *
         * GET /api/app/partidos/sin-convocatoria?equipoId=1&incluirPartidoId=5
         */
        @GetMapping("/sin-convocatoria")
        public ResponseEntity<?> obtenerSinConvocatoria(
                        @RequestParam Long equipoId,
                        @RequestParam(required = false) Long incluirPartidoId,
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

                        List<PartidoDTO> partidos = partidoService.obtenerPartidosSinConvocatoria(
                                        usuarioId,
                                        equipoId,
                                        incluirPartidoId);

                        return ResponseEntity.ok(partidos);

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
                                        .body("Error al obtener los partidos");
                }
        }

        /**
         * Eliminar un partido de un equipo que el entrenador pueda
         * gestionar.
         *
         * DELETE /api/app/partidos/{id}
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

                        Long usuarioId = Long.parseLong(
                                        authentication.getName());

                        partidoService.eliminar(
                                        usuarioId,
                                        id);

                        return ResponseEntity
                                        .noContent()
                                        .build();

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
                                        .body("Error al eliminar el partido");
                }
        }

}
