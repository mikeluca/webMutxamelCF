package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.ResultadoService;

@RestController
@RequestMapping("/api/public/resultados")
public class PublicResultadoController {

    private final ResultadoService resultadoService;

    public PublicResultadoController(
            ResultadoService resultadoService) {
        this.resultadoService = resultadoService;
    }

    @GetMapping
    public List<ResultadoDTO> obtenerResultados() {

        return resultadoService.obtenerResultados("F");
    }

    @GetMapping("/primer-equipo")
    public ResponseEntity<ResultadoDTO> obtenerResultadoPrimerEquipo() {

        ResultadoDTO resultado = resultadoService.obtenerResultadoPrimerEquipo();

        if (resultado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(resultado);
    }
}