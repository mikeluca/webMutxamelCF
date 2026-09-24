package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.service.PartidoService;

@RestController
@RequestMapping("/api/public/partidos")
public class PublicPartidoController {

    private static final int LIMITE_MINIMO = 1;
    private static final int LIMITE_MAXIMO = 20;
    private static final int LIMITE_POR_DEFECTO = 5;

    private final PartidoService partidoService;

    public PublicPartidoController(
            PartidoService partidoService) {
        this.partidoService = partidoService;
    }

    /**
     * Últimos partidos de un equipo, identificado por su ID o por su
     * nombre (exactamente uno de los dos parámetros).
     *
     * GET /api/public/partidos?equipoId=1
     * GET /api/public/partidos?equipo=Senior A
     */
    @GetMapping
    public ResponseEntity<?> obtenerUltimos(
            @RequestParam(required = false) Long equipoId,
            @RequestParam(required = false) String equipo,
            @RequestParam(defaultValue = "" + LIMITE_POR_DEFECTO) int limite) {

        boolean tieneEquipoId = equipoId != null;
        boolean tieneEquipoNombre = equipo != null && !equipo.isBlank();

        if (tieneEquipoId == tieneEquipoNombre) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Debe indicarse exactamente uno de los parámetros 'equipoId' o 'equipo'");
        }

        int limiteAcotado = Math.max(LIMITE_MINIMO, Math.min(LIMITE_MAXIMO, limite));

        List<PartidoDTO> partidos = tieneEquipoId
                ? partidoService.obtenerUltimosPorEquipo(equipoId, limiteAcotado)
                : partidoService.obtenerUltimosPorEquipoNombre(equipo, limiteAcotado);

        return ResponseEntity.ok(partidos);
    }

}
