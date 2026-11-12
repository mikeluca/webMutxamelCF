package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.FichaPartidoDTO;
import com.mikedev.mutxamelcf.service.FichaPartidoService;

/** Ficha pública del partido del Primer Equipo (docs/api-app-retransmision.md). */
@RestController
@RequestMapping("/api/public/partidos")
public class PublicFichaPartidoController {

    private final FichaPartidoService fichaPartidoService;

    public PublicFichaPartidoController(FichaPartidoService fichaPartidoService) {
        this.fichaPartidoService = fichaPartidoService;
    }

    /**
     * GET /api/public/partidos/{partidoId}/ficha. Los textos fijos van en el primer idioma admitido (es, ca, en) de
     * Accept-Language; sin él, en castellano. 404 si el partido no existe o no es del Primer Equipo.
     */
    @GetMapping("/{partidoId}/ficha")
    public ResponseEntity<FichaPartidoDTO> ficha(
            @PathVariable Long partidoId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

        FichaPartidoDTO ficha = fichaPartidoService.obtenerFicha(partidoId,
                PublicResultadoController.idioma(acceptLanguage));

        return ficha == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(ficha);
    }
}
