package com.mikedev.mutxamelcf.model;

/**
 * Un patrocinador del club, tal como lo ven la web (pie de página), la app (GET /api/public/patrocinadores) y los
 * carteles para redes sociales.
 *
 * @param nombre          el nombre (es también el texto alternativo de la imagen)
 * @param imagen          el fichero dentro de {@code static/images} de la web; {@code null} si no tiene imagen web
 * @param url             el enlace al pulsar el logo; {@code null} si no tiene (el logo se pinta sin enlace)
 * @param circular        si el logo se pinta redondo en la web
 * @param logoCartel      el PNG blanco con fondo transparente para los carteles (en
 *                        {@code redes/logos/patrocinadores-cartel}; el destacado, en {@code redes/logos}); null si no
 *                        sale en carteles
 * @param destacadoCartel si va arriba a la izquierda del cartel en vez de en el bloque de patrocinadores de abajo
 * @param enWeb           si sale en el pie de la web y en {@code GET /api/public/patrocinadores}
 * @param ordenCartel     posición en el bloque de abajo del cartel (independiente del orden de la web)
 */
public record Patrocinador(String nombre, String imagen, String url, boolean circular, String logoCartel,
        boolean destacadoCartel, boolean enWeb, int ordenCartel) {

    private static final String RUTA_LOGOS = "/redes/logos/";
    private static final String RUTA_LOGOS_PATROCINADORES = RUTA_LOGOS + "patrocinadores-cartel/";

    /** La ruta de la imagen relativa a la raíz de la web ("/images/frutasJuanete.jpg"); null si no tiene. */
    public String imagenUrl() {
        return imagen == null ? null : "/images/" + imagen;
    }

    /** El recurso del classpath con el logo del cartel, o null si no sale en carteles. */
    public String recursoLogoCartel() {

        if (logoCartel == null) {
            return null;
        }

        return (destacadoCartel ? RUTA_LOGOS : RUTA_LOGOS_PATROCINADORES) + logoCartel;
    }
}
