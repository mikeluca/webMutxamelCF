package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.RecordatorioRequest;
import com.mikedev.mutxamelcf.service.CoordinacionService;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService;

/** Paneles de coordinación: solo COORDINADOR y ADMIN_APP (lo comprueba el servicio). */
@RestController
@RequestMapping("/api/app/coordinacion")
public class AppCoordinacionController {

    private final CoordinacionService coordinacionService;
    private final RecordatorioEntrenadorService recordatorioService;

    public AppCoordinacionController(
            CoordinacionService coordinacionService,
            RecordatorioEntrenadorService recordatorioService) {

        this.coordinacionService = coordinacionService;
        this.recordatorioService = recordatorioService;
    }

    /**
     * POST /api/app/coordinacion/recordatorios {partidoId, tipo: CONVOCATORIA|RESULTADO}
     *
     * Avisa a los entrenadores del equipo. 403 al resto de roles, 400 si no procede, 409 sin
     * entrenadores con cuenta y 429 (con "codigo" y la hora del último) si se repite antes de 2 horas.
     */
    @PostMapping("/recordatorios")
    public ResponseEntity<?> recordar(
            @RequestBody RecordatorioRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            RecordatorioEntrenadorService.Enviado enviado = recordatorioService.enviar(
                    Long.valueOf(authentication.getName()), request.getPartidoId(), request.getTipo());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("destinatarios", enviado.destinatarios());
            respuesta.put("fecha", enviado.fecha());

            return ResponseEntity.ok(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (RecordatorioEntrenadorService.SinEntrenadores e) {

            Map<String, Object> conflicto = new LinkedHashMap<>();
            conflicto.put("codigo", e.codigo());
            conflicto.put("mensaje", e.getMessage());

            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicto);

        } catch (RecordatorioEntrenadorService.DemasiadoPronto e) {

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("codigo", CODIGO_RECORDATORIO_REPETIDO);
            respuesta.put("mensaje", e.getMessage());
            respuesta.put("ultimo", e.ultimo());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(respuesta);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al enviar el recordatorio");
        }
    }

    /** Valor de "codigo" del 429 de un recordatorio repetido (estable: la app lo traduce). */
    static final String CODIGO_RECORDATORIO_REPETIDO = "RECORDATORIO_REPETIDO";

    /** GET /api/app/coordinacion/fin-de-semana?fecha=yyyy-MM-dd */
    @GetMapping("/fin-de-semana")
    public ResponseEntity<?> obtenerFinDeSemana(
            @RequestParam(required = false) String fecha,
            Authentication authentication) {

        LocalDate dia;

        try {
            dia = fecha == null || fecha.isBlank() ? null : LocalDate.parse(fecha.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("La fecha debe tener el formato yyyy-MM-dd");
        }

        return responder(authentication, "Error al obtener el fin de semana",
                usuarioId -> coordinacionService.obtenerFinDeSemana(usuarioId, dia));
    }

    /** GET /api/app/coordinacion/seguimiento-jugadores?periodo=30d|temporada */
    @GetMapping("/seguimiento-jugadores")
    public ResponseEntity<?> obtenerSeguimiento(
            @RequestParam(required = false) String periodo,
            Authentication authentication) {

        return responder(authentication, "Error al obtener el seguimiento de jugadores",
                usuarioId -> coordinacionService.obtenerSeguimientoJugadores(usuarioId, periodo));
    }

    /** GET /api/app/coordinacion/seguimiento-jugadores/{jugadorId}?periodo=... */
    @GetMapping("/seguimiento-jugadores/{jugadorId}")
    public ResponseEntity<?> obtenerDetalle(
            @PathVariable Long jugadorId,
            @RequestParam(required = false) String periodo,
            Authentication authentication) {

        return responder(authentication, "Error al obtener el detalle del jugador",
                usuarioId -> coordinacionService.obtenerDetalleSeguimiento(usuarioId, jugadorId, periodo));
    }

    private ResponseEntity<?> responder(
            Authentication authentication, String errorGenerico, Function<Long, Object> consulta) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(consulta.apply(Long.valueOf(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorGenerico);
        }
    }
}
