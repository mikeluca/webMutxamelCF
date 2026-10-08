package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.EliminarCuentaAppRequest;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.service.CuentaAppService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Autoborrado de cuenta desde la app (Apple Guideline 5.1.1(v)).
 */
@RestController
@RequestMapping("/api/app/auth/cuenta")
public class AppCuentaController {

    private final CuentaAppService cuentaAppService;
    private final LoginRateLimiter rateLimiter;

    public AppCuentaController(
            CuentaAppService cuentaAppService,
            LoginRateLimiter rateLimiter) {

        this.cuentaAppService = cuentaAppService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * DELETE /api/app/auth/cuenta
     * Cuerpo: { "password": "..." }
     *
     * Borra la cuenta del usuario autenticado tras confirmar su
     * contraseña. 204 si se borra; 400 si la contraseña no es correcta.
     */
    @DeleteMapping
    public ResponseEntity<?> eliminar(
            @Valid @RequestBody EliminarCuentaAppRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        int usuarioId;

        try {
            usuarioId = Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        // Evita que un token robado se use para adivinar la contraseña.
        String clave = rateLimiter.clave(
                LoginRateLimiter.CONTEXTO_APP_BORRAR_CUENTA,
                httpRequest.getRemoteAddr(),
                String.valueOf(usuarioId));

        if (rateLimiter.estaBloqueado(clave)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.");
        }

        try {

            cuentaAppService.eliminarCuenta(usuarioId, request.getPassword());

            rateLimiter.registrarExito(clave);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {

            rateLimiter.registrarFallo(clave);

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }
}
