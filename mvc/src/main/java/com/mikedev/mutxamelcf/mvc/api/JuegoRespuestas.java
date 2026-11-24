package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.service.JuegoConflictoException;
import com.mikedev.mutxamelcf.service.JuegoLimiteEnviosException;

/**
 * Las respuestas de error del juego de Mutxigol, iguales en las tres API (pública, de la app y de administración):
 * {@code {"error": "...", "codigo": "..."}} (el código solo en los conflictos: ALIAS_REQUERIDO y ALIAS_EN_USO).
 * 400 datos que no valen, 401 sin sesión, 403 sin permiso, 404 no existe, 409 conflicto, 429 demasiados envíos.
 */
final class JuegoRespuestas {

    private static final Logger logger = LoggerFactory.getLogger(JuegoRespuestas.class);

    private JuegoRespuestas() {
    }

    /** El id de la cuenta de la sesión, o -1 si no hay sesión válida. */
    static int usuarioId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return -1;
        }

        try {
            return Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static ResponseEntity<?> sinSesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Usuario no autenticado"));
    }

    static ResponseEntity<?> ejecutar(Supplier<ResponseEntity<?>> accion) {

        try {
            return accion.get();

        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage(), null);

        } catch (NoSuchElementException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage(), null);

        } catch (JuegoConflictoException e) {
            return error(HttpStatus.CONFLICT, e.getMessage(), e.getCodigo());

        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage(), null);

        } catch (JuegoLimiteEnviosException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);

        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage(), null);

        } catch (RuntimeException e) {
            logger.error("Error en el juego de Mutxigol", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error en el juego", null);
        }
    }

    private static ResponseEntity<?> error(HttpStatus estado, String mensaje, String codigo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", mensaje);

        if (codigo != null) {
            cuerpo.put("codigo", codigo);
        }

        return ResponseEntity.status(estado).body(cuerpo);
    }
}
