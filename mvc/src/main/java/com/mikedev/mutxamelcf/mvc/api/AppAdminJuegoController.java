package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.JuegoService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Moderar el juego de Mutxigol desde la app (solo ADMIN_APP: 403 al resto). Lo mismo que el panel web
 * (/admin/juego-mutxigol): ver los perfiles, cambiar un alias, ocultar o mostrar en el ranking y borrar puntuaciones.
 * A diferencia del ranking público, aquí sí salen el nombre y el email de la cuenta (solo para el administrador).
 */
@RestController
@RequestMapping("/api/app/admin/juego")
public class AppAdminJuegoController {

    private static final String SOLO_ADMIN = "Solo un administrador puede moderar el juego";

    public record AliasRequest(String alias) {
    }

    public record OcultoRequest(Boolean oculto) {
    }

    private final JuegoService juegoService;
    private final UsuarioAppService usuarioAppService;

    public AppAdminJuegoController(JuegoService juegoService, UsuarioAppService usuarioAppService) {
        this.juegoService = juegoService;
        this.usuarioAppService = usuarioAppService;
    }

    /** Qué hace una ruta, ya comprobado que quien llama es ADMIN_APP. */
    @FunctionalInterface
    private interface Accion {
        ResponseEntity<?> ejecutar();
    }

    private ResponseEntity<?> comoAdmin(Authentication authentication, Accion accion) {

        int solicitanteId = JuegoRespuestas.usuarioId(authentication);

        if (solicitanteId <= 0) {
            return JuegoRespuestas.sinSesion();
        }

        return JuegoRespuestas.ejecutar(() -> {
            usuarioAppService.exigirAdminApp(solicitanteId, SOLO_ADMIN);

            return accion.ejecutar();
        });
    }

    /** GET /api/app/admin/juego/perfiles?alias= */
    @GetMapping("/perfiles")
    public ResponseEntity<?> listar(@RequestParam(name = "alias", required = false) String alias,
            Authentication authentication) {

        return comoAdmin(authentication, () -> ResponseEntity.ok(juegoService.listarPerfilesAdmin(alias)));
    }

    /** GET /api/app/admin/juego/perfiles/{usuarioId}/puntuaciones: las últimas partidas, para borrar una. */
    @GetMapping("/perfiles/{usuarioId}/puntuaciones")
    public ResponseEntity<?> puntuaciones(@PathVariable long usuarioId, Authentication authentication) {

        return comoAdmin(authentication, () -> ResponseEntity.ok(juegoService.puntuacionesAdmin(usuarioId)));
    }

    /** PUT /api/app/admin/juego/perfiles/{usuarioId}/alias {alias} */
    @PutMapping("/perfiles/{usuarioId}/alias")
    public ResponseEntity<?> cambiarAlias(@PathVariable long usuarioId, @RequestBody(required = false) AliasRequest body,
            Authentication authentication) {

        return comoAdmin(authentication, () -> ResponseEntity
                .ok(juegoService.cambiarAliasAdmin(usuarioId, body == null ? null : body.alias())));
    }

    /** PUT /api/app/admin/juego/perfiles/{usuarioId}/oculto {oculto: true|false} */
    @PutMapping("/perfiles/{usuarioId}/oculto")
    public ResponseEntity<?> ocultar(@PathVariable long usuarioId, @RequestBody(required = false) OcultoRequest body,
            Authentication authentication) {

        return comoAdmin(authentication, () -> {

            if (body == null || body.oculto() == null) {
                throw new IllegalArgumentException("Indica si se oculta (oculto: true) o se muestra (oculto: false)");
            }

            return ResponseEntity.ok(juegoService.ocultarAdmin(usuarioId, body.oculto()));
        });
    }

    /** DELETE /api/app/admin/juego/puntuaciones/{id} */
    @DeleteMapping("/puntuaciones/{id}")
    public ResponseEntity<?> borrarPuntuacion(@PathVariable long id, Authentication authentication) {

        return comoAdmin(authentication, () -> {
            juegoService.borrarPuntuacionAdmin(id);

            return ResponseEntity.noContent().build();
        });
    }

    /** DELETE /api/app/admin/juego/perfiles/{usuarioId}/puntuaciones: todas las del perfil; devuelve cuántas. */
    @DeleteMapping("/perfiles/{usuarioId}/puntuaciones")
    public ResponseEntity<?> borrarTodas(@PathVariable long usuarioId, Authentication authentication) {

        return comoAdmin(authentication,
                () -> ResponseEntity.ok(Map.of("borradas", juegoService.borrarPuntuacionesAdmin(usuarioId))));
    }
}
