package com.mikedev.mutxamelcf.model;

import java.util.Comparator;
import java.util.List;

/**
 * La lista de patrocinadores del club: el ÚNICO sitio donde se añaden, quitan o reordenan patrocinadores. La usan el
 * pie de página de la web ({@code footer.html}), la app ({@code GET /api/public/patrocinadores}) y los carteles para
 * redes sociales; no hay que tocar nada más (docs/api-public-patrocinadores.md).
 *
 * <p>
 * El orden de {@link #LISTA} es el orden en que se ven en la web. El de los carteles es independiente
 * ({@code ordenCartel}). La imagen web va en {@code mvc/src/main/resources/static/images} y el logo del cartel en
 * {@code service/src/main/resources/redes/logos/patrocinadores-cartel}; aquí solo se pone el nombre de fichero (un
 * test comprueba que existen).
 */
public final class PatrocinadoresClub {

    public static final List<Patrocinador> LISTA = List.of(
            new Patrocinador("Frutas y verduras Juanete", "frutasJuanete.jpg",
                    "https://www.instagram.com/frutasjuanete/", false, "frutas-juanete.png", false, true, 2),
            new Patrocinador("Unlimited Developments", "unlimited.jpg", "https://www.unlimiteddevelopments.es/",
                    false, "unlimited.png", false, true, 4),
            new Patrocinador("Ayto Mutxamel", "aytoMutxamel.jpg", "https://ayto.mutxamel.org/", false,
                    "ayuntamiento-blanco.png", true, true, 0),
            new Patrocinador("La bodega de Elias", "laBodega.jpg", "https://www.instagram.com/labodegaelias/", true,
                    "la-bodega.png", false, true, 1),
            new Patrocinador("Aresala", "aresala.jpg", null, false, "aresala.png", false, true, 5),
            new Patrocinador("AA Energy", "aaenergy.jpeg", "https://www.aaenergyenriquez.es", false, "aa-energy.png",
                    false, true, 6),
            new Patrocinador("Dental Company Mutxamel", "dentalcompany.jpg",
                    "https://clinicas.dentalcompany.es/comunidad-valenciana/alicante/mutxamel/clinica-dental-en-mutxamel",
                    false, "dental-company.png", false, true, 7),
            // Solo en carteles: ni en la web ni en la API pública.
            new Patrocinador("Mercury", null, null, false, "mercury.png", false, false, 3));

    private PatrocinadoresClub() {
    }

    /** Los de la web y la API pública, en su orden. */
    public static List<Patrocinador> paraWeb() {
        return LISTA.stream().filter(Patrocinador::enWeb).toList();
    }

    /** Los del bloque de abajo del cartel (con logo y no destacados), en su {@code ordenCartel}. */
    public static List<Patrocinador> paraCartel() {
        return LISTA.stream().filter(p -> p.logoCartel() != null && !p.destacadoCartel())
                .sorted(Comparator.comparingInt(Patrocinador::ordenCartel)).toList();
    }

    /** El patrocinador destacado del cartel (arriba a la izquierda), o null si no hay. */
    public static Patrocinador destacadoCartel() {
        return LISTA.stream().filter(p -> p.logoCartel() != null && p.destacadoCartel()).findFirst().orElse(null);
    }
}
