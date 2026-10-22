package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versiones de la app móvil que acepta el servidor, por plataforma, y las fichas de las
 * tiendas. Público (/api/public/**): la app lo consulta antes de iniciar sesión.
 */
@RestController
@RequestMapping("/api/public/app")
public class PublicAppVersionController {

    private final String minimaAndroid;
    private final String minimaIos;
    private final String recomendadaAndroid;
    private final String recomendadaIos;
    private final String urlAndroid;
    private final String urlIos;

    public PublicAppVersionController(
            @Value("${app.version.minima.android}") String minimaAndroid,
            @Value("${app.version.minima.ios}") String minimaIos,
            @Value("${app.version.recomendada.android}") String recomendadaAndroid,
            @Value("${app.version.recomendada.ios}") String recomendadaIos,
            @Value("${app.url.android}") String urlAndroid,
            @Value("${app.url.ios}") String urlIos) {

        this.minimaAndroid = minimaAndroid;
        this.minimaIos = minimaIos;
        this.recomendadaAndroid = recomendadaAndroid;
        this.recomendadaIos = recomendadaIos;
        this.urlAndroid = urlAndroid;
        this.urlIos = urlIos;
    }

    @GetMapping("/version")
    public Map<String, String> version() {

        Map<String, String> respuesta = new LinkedHashMap<>();

        respuesta.put("minimaAndroid", minimaAndroid);
        respuesta.put("minimaIos", minimaIos);
        respuesta.put("recomendadaAndroid", recomendadaAndroid);
        respuesta.put("recomendadaIos", recomendadaIos);
        respuesta.put("urlAndroid", urlAndroid);
        respuesta.put("urlIos", urlIos);

        return respuesta;
    }
}
