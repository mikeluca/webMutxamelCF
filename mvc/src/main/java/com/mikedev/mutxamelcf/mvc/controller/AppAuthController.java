package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.RecuperacionContrasena;
import com.mikedev.mutxamelcf.model.RecuperarPasswordRequest;
import com.mikedev.mutxamelcf.model.RestablecerPasswordRequest;
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
import java.util.Map;

import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    static final String CODIGO_DEMASIADOS_INTENTOS = "DEMASIADOS_INTENTOS";

    private final UsuarioAppService usuarioAppService;
    private final LoginRateLimiter rateLimiter;
    private final CuentaActivadaNotifier cuentaActivadaNotifier;
    private final RecuperacionContrasena recuperacionContrasena;

    public AppAuthController(
            UsuarioAppService usuarioAppService,
            LoginRateLimiter rateLimiter,
            CuentaActivadaNotifier cuentaActivadaNotifier,
            RecuperacionContrasena recuperacionContrasena) {

        this.usuarioAppService = usuarioAppService;
        this.rateLimiter = rateLimiter;
        this.cuentaActivadaNotifier = cuentaActivadaNotifier;
        this.recuperacionContrasena = recuperacionContrasena;
    }

    /**
     * Login de usuarios de la aplicación móvil.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginAppRequest request,
            HttpServletRequest httpRequest) {

        String clave = rateLimiter.clave(
                LoginRateLimiter.CONTEXTO_APP_LOGIN,
                httpRequest.getRemoteAddr(),
                request.getEmail());

        if (rateLimiter.estaBloqueado(clave)) {

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.");
        }

        try {

            LoginAppResponse response = usuarioAppService.login(
                    request.getEmail(),
                    request.getPassword());

            rateLimiter.registrarExito(clave);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            // SEC-06: mismo 401 para email inexistente, cuenta inactiva
            // o contraseña incorrecta (ver UsuarioAppServiceImpl.login),
            // y siempre cuenta como intento fallido para el rate limiter.
            rateLimiter.registrarFallo(clave);

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
            @Valid @RequestBody ActivarCuentaAppRequest request,
            HttpServletRequest httpRequest) {

        String clave = rateLimiter.clave(
                LoginRateLimiter.CONTEXTO_APP_ACTIVAR,
                httpRequest.getRemoteAddr(),
                request.getEmail());

        if (rateLimiter.estaBloqueado(clave)) {

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos.");
        }

        try {

            UsuarioApp usuario = usuarioAppService.activarCuenta(
                    request.getEmail(),
                    request.getCodigo(),
                    request.getPassword());

            rateLimiter.registrarExito(clave);

            cuentaActivadaNotifier.notificar(usuario.getId());

            LoginAppResponse loginResponse = usuarioAppService.login(
                    usuario.getEmail(),
                    request.getPassword());

            return ResponseEntity.ok(loginResponse);

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            rateLimiter.registrarFallo(clave);

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }

    /**
     * "¿Has olvidado tu contraseña?". Responde SIEMPRE 204, exista o no el email, esté
     * la cuenta activa o no y se superen o no los límites: no revela qué emails tienen
     * cuenta (ver {@link RecuperacionContrasena}).
     */
    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperar(
            @Valid @RequestBody RecuperarPasswordRequest request,
            HttpServletRequest httpRequest) {

        recuperacionContrasena.solicitar(request.getEmail(), httpRequest.getRemoteAddr());

        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la contraseña con el código recibido por email y, en el mismo paso, inicia
     * sesión: la respuesta es un LoginAppResponse igual que el de /login. Los JWT
     * emitidos antes del cambio dejan de valer (JwtAuthenticationFilter).
     */
    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(
            @Valid @RequestBody RestablecerPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clave = rateLimiter.clave(
                LoginRateLimiter.CONTEXTO_APP_RESTABLECER,
                httpRequest.getRemoteAddr(),
                request.getEmail());

        if (rateLimiter.estaBloqueado(clave)) {

            // "codigo" es estable: la app lo traduce a su idioma.
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "codigo", CODIGO_DEMASIADOS_INTENTOS,
                            "mensaje", "Demasiados intentos fallidos. Inténtalo de nuevo en unos minutos."));
        }

        try {

            // La transacción se confirma al volver de este método.
            UsuarioApp usuario = usuarioAppService.restablecerPassword(
                    request.getEmail(),
                    request.getCodigo(),
                    request.getPassword());

            rateLimiter.registrarExito(clave);

            recuperacionContrasena.avisarContrasenaCambiada(usuario.getId());

            LoginAppResponse loginResponse = usuarioAppService.login(
                    usuario.getEmail(),
                    request.getPassword());

            return ResponseEntity.ok(loginResponse);

        } catch (IllegalArgumentException e) {

            rateLimiter.registrarFallo(clave);

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

            response.setNombre(usuarioAppService.obtenerNombreMostrable(usuarioId));

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }
    }
}