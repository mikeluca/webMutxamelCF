package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.IdiomaApp;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.PartidoService;

@RestController
@RequestMapping("/api/public/resultados")
public class PublicResultadoController {

    private final PartidoService partidoService;

    public PublicResultadoController(
            PartidoService partidoService) {
        this.partidoService = partidoService;
    }

    @GetMapping
    public List<ResultadoDTO> obtenerResultados() {

        return partidoService.obtenerResultados("F");
    }

    /**
     * Endpoint público sin idioma: los textos van en castellano salvo que la app mande Accept-Language (es, ca o en),
     * que solo afecta al "Gol en propia puerta del rival" de los goleadores.
     */
    @GetMapping("/primer-equipo")
    public ResponseEntity<ResultadoDTO> obtenerResultadoPrimerEquipo(
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

        // Siempre hay resultado: si no hay próximo partido ni último
        // jugado, el servicio devuelve un ResultadoDTO con rival vacío
        // (misma forma que una jornada de descanso).
        return ResponseEntity.ok(partidoService.obtenerResultadoPrimerEquipo(idioma(acceptLanguage)));
    }

    /** El primer idioma admitido (es, ca, en) de la cabecera, por orden de preferencia; null si no hay ninguno. */
    static String idioma(String acceptLanguage) {

        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }

        try {
            for (Locale.LanguageRange rango : Locale.LanguageRange.parse(acceptLanguage)) {

                String idioma = rango.getRange().split("-")[0].toLowerCase(Locale.ROOT);

                if (IdiomaApp.TODOS.contains(idioma)) {
                    return idioma;
                }
            }
        } catch (IllegalArgumentException e) {
            // Cabecera mal formada: se ignora y se contesta en castellano.
        }

        return null;
    }
}
