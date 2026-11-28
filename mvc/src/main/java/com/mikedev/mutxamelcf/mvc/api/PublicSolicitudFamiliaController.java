package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.mvc.config.PublicFormRateLimiter;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Registro;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * "Soy familiar de un jugador": el registro de una familia SIN sesión (docs/api-app-solicitudes-familia.md).
 *
 * <p>
 * Nunca devuelve si un jugador existe ni datos de jugadores. Solicitud, verificación y reenvío del código están
 * limitados con {@link PublicFormRateLimiter} por IP y por email.
 */
@RestController
@RequestMapping("/api/public/solicitudes-familia")
public class PublicSolicitudFamiliaController {

    private static final Logger logger = LoggerFactory.getLogger(PublicSolicitudFamiliaController.class);

    static final String MENSAJE_DEMASIADOS_INTENTOS = "Demasiados intentos. Inténtalo de nuevo en unos minutos.";

    static final String MENSAJE_REGISTRO_NO_DISPONIBLE = "El registro de familias estará disponible próximamente. "
            + "Mientras tanto, pide la invitación a tu entrenador o al club.";

    /** Cuerpo de POST /verificar. */
    public record VerificarRequest(String email, String codigo, String password, String idioma) {
    }

    /** Cuerpo de POST /reenviar-codigo. */
    public record ReenviarRequest(String email) {
    }

    private final SolicitudFamiliaService solicitudFamiliaService;
    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;
    private final PublicFormRateLimiter formRateLimiter;

    /** Interruptor app.registro-familias.habilitado (APP_REGISTRO_FAMILIAS): con false no se crea ninguna solicitud. */
    private final boolean registroHabilitado;

    public PublicSolicitudFamiliaController(SolicitudFamiliaService solicitudFamiliaService,
            UsuarioAppService usuarioAppService, ComunicacionesService comunicacionesService,
            PublicFormRateLimiter formRateLimiter,
            @Value("${app.registro-familias.habilitado:false}") boolean registroHabilitado) {

        this.registroHabilitado = registroHabilitado;
        this.solicitudFamiliaService = solicitudFamiliaService;
        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
        this.formRateLimiter = formRateLimiter;
    }

    /**
     * POST /api/public/solicitudes-familia. Crea la cuenta sin roles y manda el código de 6 dígitos por email. 200 con
     * {@code estado}: CODIGO_ENVIADO, CUENTA_EXISTENTE (que inicie sesión y pida el acceso desde dentro) o
     * INVITACION_PENDIENTE (que use "Activar cuenta" con el código recibido). 400 con el mensaje si algo no vale, 429 si
     * supera el límite y 503 si el email no ha salido. 403 {@code REGISTRO_NO_DISPONIBLE} si el interruptor
     * app.registro-familias.habilitado está a false (no se crea nada).
     */
    @PostMapping
    public ResponseEntity<?> solicitar(@RequestBody(required = false) SolicitudFamiliaRequest request,
            HttpServletRequest http) {

        // Antes de nada: ni cuenta, ni solicitud, ni email, ni consumir el límite de intentos. /verificar y
        // /reenviar-codigo siguen abiertos para no dejar colgadas las solicitudes que ya estaban a medias.
        if (!registroHabilitado) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("estado", "REGISTRO_NO_DISPONIBLE",
                    "mensaje", MENSAJE_REGISTRO_NO_DISPONIBLE));
        }

        String email = request == null ? null : request.email();

        if (!permitir("solicitud", http, email)) {
            return demasiados();
        }

        try {

            Registro registro = solicitudFamiliaService.registrarPublica(request);

            switch (registro.resultado()) {
                case CUENTA_EXISTENTE:
                    return ResponseEntity.ok(Map.of("estado", "CUENTA_EXISTENTE", "mensaje",
                            "Ya tienes una cuenta con ese email. Inicia sesión y pide el acceso desde dentro de la app."));
                case INVITACION_PENDIENTE:
                    return ResponseEntity.ok(Map.of("estado", "INVITACION_PENDIENTE", "mensaje",
                            "El club ya te ha enviado un código de activación. Usa «Activar cuenta» con ese código."));
                default:
                    return enviarCodigo(registro);
            }

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }

    /**
     * POST /api/public/solicitudes-familia/verificar. Confirma el email con el código y fija la contraseña; la solicitud
     * pasa a PENDIENTE y la respuesta es un LoginAppResponse como el de /login (la cuenta entra sin roles: solo ve lo
     * público y el estado de su solicitud).
     */
    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@RequestBody(required = false) VerificarRequest request,
            HttpServletRequest http) {

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "La petición no puede ser nula"));
        }

        if (!permitir("verificar", http, request.email())) {
            return demasiados();
        }

        try {

            UsuarioApp usuario = solicitudFamiliaService.verificarEmail(request.email(), request.codigo(),
                    request.password());

            if (request.idioma() != null) {
                usuarioAppService.actualizarIdioma(usuario.getId(), request.idioma());
            }

            LoginAppResponse login = usuarioAppService.login(usuario.getEmail(), request.password());

            return ResponseEntity.ok(login);

        } catch (IllegalStateException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage()));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }

    /**
     * POST /api/public/solicitudes-familia/reenviar-codigo. Responde SIEMPRE 204 (salvo 429 por el límite), exista o no
     * el email y tenga o no una solicitud por confirmar: no revela nada de la cuenta.
     */
    @PostMapping("/reenviar-codigo")
    public ResponseEntity<?> reenviar(@RequestBody(required = false) ReenviarRequest request,
            HttpServletRequest http) {

        String email = request == null ? null : request.email();

        if (!permitir("reenvio", http, email)) {
            return demasiados();
        }

        try {

            Registro registro = solicitudFamiliaService.reenviarCodigo(email);

            if (registro != null) {
                comunicacionesService.enviarCodigoSolicitudFamilia(registro.email(), registro.nombre(),
                        registro.codigo(), registro.idioma());
            }

        } catch (RuntimeException e) {
            logger.warn("No se ha podido reenviar el código de una solicitud de familia: {}", e.getMessage());
        }

        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> enviarCodigo(Registro registro) {

        boolean enviado = comunicacionesService.enviarCodigoSolicitudFamilia(registro.email(), registro.nombre(),
                registro.codigo(), registro.idioma());

        if (!enviado) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("estado", "ENVIO_FALLIDO",
                    "mensaje", "No se ha podido enviar el email. Inténtalo de nuevo en unos minutos."));
        }

        return ResponseEntity.ok(Map.of("estado", "CODIGO_ENVIADO", "mensaje",
                "Te hemos enviado un código por email para confirmar tu solicitud."));
    }

    /** Por IP y por email, cada operación con su propio contador. */
    private boolean permitir(String operacion, HttpServletRequest http, String email) {

        String normalizado = email == null ? "" : email.trim().toLowerCase();

        boolean porIp = formRateLimiter.permitir("solicitud-familia/" + operacion + "/ip/" + http.getRemoteAddr());
        boolean porEmail = normalizado.isEmpty()
                || formRateLimiter.permitir("solicitud-familia/" + operacion + "/email/" + normalizado);

        if (!porIp || !porEmail) {
            logger.warn("Solicitud de familia ({}) rechazada por límite de intentos: ip={}", operacion,
                    http.getRemoteAddr());
        }

        return porIp && porEmail;
    }

    private static ResponseEntity<?> demasiados() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("codigo", "DEMASIADOS_INTENTOS", "mensaje", MENSAJE_DEMASIADOS_INTENTOS));
    }
}
