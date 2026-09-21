package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.model.ActivarCuentaAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.UsuarioAppMeResponse;

import java.util.List;

import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final UsuarioAppService usuarioAppService;

    public AppAuthController(
            UsuarioAppService usuarioAppService) {

        this.usuarioAppService = usuarioAppService;
    }

    /**
     * Login de usuarios de la aplicación móvil.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginAppRequest request) {

        try {

            LoginAppResponse response = usuarioAppService.login(
                    request.getEmail(),
                    request.getPassword());

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        }
    }

    /**
     * Activación inicial de una cuenta.
     *
     * Deja la cuenta activa con la contraseña elegida y, en el mismo
     * paso, inicia sesión: la respuesta es un LoginAppResponse igual
     * que el de /login, para que la app entre directamente en el
     * Área Club sin pedir las credenciales otra vez.
     */
    @PostMapping("/activar")
    public ResponseEntity<?> activar(
            @Valid @RequestBody ActivarCuentaAppRequest request) {

        try {

            UsuarioApp usuario = usuarioAppService.activarCuenta(
                    request.getToken(),
                    request.getPassword());

            LoginAppResponse loginResponse = usuarioAppService.login(
                    usuario.getEmail(),
                    request.getPassword());

            return ResponseEntity.ok(loginResponse);

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> obtenerUsuarioActual(
            Authentication authentication) {

        try {

            int usuarioId = Integer.parseInt(
                    authentication.getName());

            UsuarioApp usuario = usuarioAppService.obtenerPorId(
                    usuarioId);

            if (usuario == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body("Usuario no encontrado");
            }

            List<RolApp> roles = usuarioAppService.obtenerRoles(
                    usuarioId);

            List<String> codigosRoles = roles.stream()
                    .map(RolApp::getCodigo)
                    .toList();

            UsuarioAppMeResponse response = new UsuarioAppMeResponse(
                    usuario.getId(),
                    usuario.getEmail(),
                    codigosRoles);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }
    }
}