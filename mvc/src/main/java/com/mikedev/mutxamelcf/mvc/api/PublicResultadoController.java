package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/primer-equipo")
    public ResponseEntity<ResultadoDTO> obtenerResultadoPrimerEquipo() {

        ResultadoDTO resultado = partidoService.obtenerResultadoPrimerEquipo();

        if (resultado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(resultado);
    }
}
