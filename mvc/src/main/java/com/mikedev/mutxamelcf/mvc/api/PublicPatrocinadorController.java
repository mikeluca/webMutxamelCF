package com.mikedev.mutxamelcf.mvc.api;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.Patrocinador;
import com.mikedev.mutxamelcf.model.PatrocinadoresClub;

/**
 * Los patrocinadores del club para la app (docs/api-public-patrocinadores.md). Público, sin datos personales y con
 * caché de 1 hora. La lista es la misma que pinta el pie de la web: {@link PatrocinadoresClub}.
 */
@RestController
@RequestMapping("/api/public/patrocinadores")
public class PublicPatrocinadorController {

    /** Un patrocinador en la respuesta; {@code imagenUrl} es relativa a la raíz de la web. */
    public record PatrocinadorResponse(String nombre, String imagenUrl, String url, boolean circular) {

        static PatrocinadorResponse de(Patrocinador p) {
            return new PatrocinadorResponse(p.nombre(), p.imagenUrl(), p.url(), p.circular());
        }
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<PatrocinadorResponse>> listar() {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(PatrocinadoresClub.paraWeb().stream().map(PatrocinadorResponse::de).toList());
    }
}
