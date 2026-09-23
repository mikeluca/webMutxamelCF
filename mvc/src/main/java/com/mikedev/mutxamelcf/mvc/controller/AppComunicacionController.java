package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PutMapping;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ComunicacionRequest;
import com.mikedev.mutxamelcf.model.ComunicacionResponse;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacionResponse;
import com.mikedev.mutxamelcf.model.MensajeConversacionResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/comunicaciones")
public class AppComunicacionController {

        private final ComunicacionService comunicacionService;

        public AppComunicacionController(
                        ComunicacionService comunicacionService) {

                this.comunicacionService = comunicacionService;
        }

        /**
         * Crear una comunicación.
         *
         * POST /api/app/comunicaciones
         */
        @PostMapping
        public ResponseEntity<?> crear(
                        @Valid @RequestBody ComunicacionRequest request,
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

                        Comunicacion comunicacion = new Comunicacion();

                        comunicacion.setTitulo(
                                        request.getTitulo());

                        comunicacion.setContenido(
                                        request.getContenido());

                        Comunicacion creada = comunicacionService.crear(
                                        comunicacion,
                                        request.getEquipoIds(),
                                        request.getCategorias(),
                                        request.getDestinatariosIds(),
                                        usuarioId);

                        return ResponseEntity
                                        .status(HttpStatus.CREATED)
                                        .body(creada);

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
                                        .body("Error al crear la comunicación");
                }
        }

        /**
         * Obtener las comunicaciones del usuario autenticado.
         *
         * GET /api/app/comunicaciones
         */
        @GetMapping
        public ResponseEntity<?> obtenerParaUsuario(
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

                        List<ComunicacionResponse> comunicaciones = comunicacionService.listarParaUsuario(
                                        usuarioId);

                        return ResponseEntity.ok(
                                        comunicaciones);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener las comunicaciones");
                }
        }

        /**
         * Conversaciones privadas del usuario autenticado (una
         * entrada por contraparte, con el último mensaje).
         *
         * GET /api/app/comunicaciones/conversaciones
         */
        @GetMapping("/conversaciones")
        public ResponseEntity<?> listarConversaciones(
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

                        List<ComunicacionResponse> conversaciones = comunicacionService
                                        .listarConversacionesParaUsuario(usuarioId);

                        return ResponseEntity.ok(
                                        conversaciones);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener las conversaciones");
                }
        }

        /**
         * Obtener una comunicación concreta.
         *
         * GET /api/app/comunicaciones/{id}
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

                        Comunicacion comunicacion = comunicacionService.obtenerPorId(
                                        id,
                                        usuarioId);

                        return ResponseEntity.ok(
                                        comunicacion);

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
                                        .body("Error al obtener la comunicación");
                }
        }

        /**
         * Eliminar una comunicación.
         *
         * DELETE /api/app/comunicaciones/{id}
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

                        comunicacionService.eliminar(
                                        id,
                                        usuarioId);

                        return ResponseEntity.noContent()
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
                                        .status(HttpStatus.NOT_FOUND)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al eliminar la comunicación");
                }
        }

        /**
         * Obtener las comunicaciones enviadas por el usuario autenticado.
         *
         * GET /api/app/comunicaciones/enviadas
         */
        @GetMapping("/enviadas")
        public ResponseEntity<?> obtenerEnviadas(
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

                        List<ComunicacionResponse> comunicaciones = comunicacionService.listarEnviadasParaUsuario(
                                        usuarioId);

                        return ResponseEntity.ok(
                                        comunicaciones);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener las comunicaciones enviadas");
                }
        }

        @GetMapping("/destinatarios")
        public ResponseEntity<?> obtenerDestinatarios(
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(authentication.getName());

                        List<DestinatarioComunicacionResponse> destinatarios = comunicacionService
                                        .obtenerDestinatariosDirectos(
                                                        usuarioId);

                        return ResponseEntity.ok(destinatarios);

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al obtener los destinatarios");
                }
        }

        /**
         * Hilo completo de la conversación privada con otro usuario.
         *
         * GET /api/app/comunicaciones/conversacion/{otroUsuarioId}
         */
        @GetMapping("/conversacion/{otroUsuarioId}")
        public ResponseEntity<?> obtenerConversacion(
                        @PathVariable Long otroUsuarioId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(authentication.getName());

                        List<MensajeConversacionResponse> mensajes = comunicacionService
                                        .obtenerConversacion(usuarioId, otroUsuarioId);

                        return ResponseEntity.ok(mensajes);

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
                                        .body("Error al obtener la conversación");
                }
        }

        /**
         * Marca como leídos todos los mensajes de la conversación
         * privada con otroUsuarioId.
         *
         * PUT /api/app/comunicaciones/conversacion/{otroUsuarioId}/leida
         */
        @PutMapping("/conversacion/{otroUsuarioId}/leida")
        public ResponseEntity<?> marcarConversacionLeida(
                        @PathVariable Long otroUsuarioId,
                        Authentication authentication) {

                if (authentication == null
                                || !authentication.isAuthenticated()) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");
                }

                try {

                        Long usuarioId = Long.parseLong(authentication.getName());

                        comunicacionService.marcarConversacionLeida(usuarioId, otroUsuarioId);

                        return ResponseEntity.noContent().build();

                } catch (NumberFormatException e) {

                        return ResponseEntity
                                        .status(HttpStatus.UNAUTHORIZED)
                                        .body("Usuario no autenticado");

                } catch (SecurityException e) {

                        return ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(e.getMessage());

                } catch (Exception e) {

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Error al marcar la conversación como leída");
                }
        }
}