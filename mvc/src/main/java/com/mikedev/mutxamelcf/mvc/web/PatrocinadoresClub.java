package com.mikedev.mutxamelcf.mvc.web;

import java.util.List;

/**
 * La lista de patrocinadores del club: el ÚNICO sitio donde se añaden, quitan o reordenan patrocinadores. La usan el
 * pie de página de la web ({@code footer.html}, vía {@link PatrocinadoresFooterAdvice}) y la app
 * ({@code GET /api/public/patrocinadores}); no hay que tocar nada más (docs/api-public-patrocinadores.md).
 *
 * <p>
 * El orden de la lista es el orden en que se ven. La imagen va en {@code mvc/src/main/resources/static/images} y aquí
 * solo se pone su nombre de fichero (un test comprueba que existe).
 */
public final class PatrocinadoresClub {

    public static final List<Patrocinador> LISTA = List.of(
            new Patrocinador("Frutas y verduras Juanete", "frutasJuanete.jpg",
                    "https://www.instagram.com/frutasjuanete/", false),
            new Patrocinador("Unlimited Developments", "unlimited.jpg", "https://www.unlimiteddevelopments.es/",
                    false),
            new Patrocinador("Ayto Mutxamel", "aytoMutxamel.jpg", "https://ayto.mutxamel.org/", false),
            new Patrocinador("La bodega de Elias", "laBodega.jpg", "https://www.instagram.com/labodegaelias/", true),
            new Patrocinador("Aresala", "aresala.jpg", null, false),
            new Patrocinador("AA Energy", "aaenergy.jpeg", "https://www.aaenergyenriquez.es", false),
            new Patrocinador("Dental Company Mutxamel", "dentalcompany.jpg",
                    "https://clinicas.dentalcompany.es/comunidad-valenciana/alicante/mutxamel/clinica-dental-en-mutxamel",
                    false));

    private PatrocinadoresClub() {
    }
}
