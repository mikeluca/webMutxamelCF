package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.CalendarioResponse;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.PartidoService;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

/**
 * Endpoint combinado de calendario de un equipo: sesiones de
 * entrenamiento + partidos en el mismo rango de fechas, para que la
 * app pinte un mes completo con una sola llamada. Delega íntegramente
 * en SesionEntrenamientoService/PartidoService, ya expuestos por
 * separado en AppSesionEntrenamientoController/AppPartidoController.
 */
@RestController
@RequestMapping("/api/app/calendario")
public class AppCalendarioController {

    private final SesionEntrenamientoService sesionEntrenamientoService;
    private final PartidoService partidoService;

    public AppCalendarioController(
            SesionEntrenamientoService sesionEntrenamientoService,
            PartidoService partidoService) {

        this.sesionEntrenamientoService = sesionEntrenamientoService;
        this.partidoService = partidoService;
    }

    /**
     * GET /api/app/calendario?equipoId=1&desde=2026-10-01&hasta=2026-10-31
     */
    @GetMapping
    public ResponseEntity<?> obtenerCalendario(
            @RequestParam Long equipoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            List<SesionEntrenamientoResponse> sesiones = sesionEntrenamientoService.obtenerPorEquipoYRango(
                    equipoId, desde, hasta);

            List<PartidoDTO> partidos = partidoService.obtenerPorEquipoYRangoFechas(equipoId, desde, hasta);

            return ResponseEntity.ok(new CalendarioResponse(sesiones, partidos));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener el calendario");
        }
    }

}
