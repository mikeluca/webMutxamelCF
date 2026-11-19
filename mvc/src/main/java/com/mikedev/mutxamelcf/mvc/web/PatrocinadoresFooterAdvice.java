package com.mikedev.mutxamelcf.mvc.web;

import java.util.List;

import com.mikedev.mutxamelcf.model.Patrocinador;
import com.mikedev.mutxamelcf.model.PatrocinadoresClub;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Pone la lista de patrocinadores en el modelo de TODAS las vistas, para el pie de página ({@code footer.html}). */
@ControllerAdvice
public class PatrocinadoresFooterAdvice {

    @ModelAttribute("patrocinadoresFooter")
    public List<Patrocinador> patrocinadoresFooter() {
        return PatrocinadoresClub.paraWeb();
    }
}
