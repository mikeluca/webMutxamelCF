package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Funciones que el servidor tiene activadas y la app consulta sin sesión para mostrarlas u ocultarlas, sin publicar
 * otra versión (docs/api-public-app-version.md). Público (/api/public/**), como /version.
 */
@RestController
@RequestMapping("/api/public/app")
public class PublicAppFuncionesController {

    private final boolean registroFamiliasHabilitado;

    public PublicAppFuncionesController(
            @Value("${app.registro-familias.habilitado:false}") boolean registroFamiliasHabilitado) {

        this.registroFamiliasHabilitado = registroFamiliasHabilitado;
    }

    @GetMapping("/funciones")
    public Map<String, Boolean> funciones() {

        Map<String, Boolean> respuesta = new LinkedHashMap<>();

        respuesta.put("registroFamiliasHabilitado", registroFamiliasHabilitado);

        return respuesta;
    }
}
