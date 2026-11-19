package com.mikedev.mutxamelcf.mvc.web;

/**
 * Un patrocinador del club, tal como lo ven la web (pie de página) y la app (GET /api/public/patrocinadores).
 *
 * @param nombre   el nombre (es también el texto alternativo de la imagen)
 * @param imagen   el fichero dentro de {@code static/images}
 * @param url      el enlace al pulsar el logo; {@code null} si no tiene (el logo se pinta sin enlace)
 * @param circular si el logo se pinta redondo
 */
public record Patrocinador(String nombre, String imagen, String url, boolean circular) {

    /** La ruta de la imagen relativa a la raíz de la web ("/images/frutasJuanete.jpg"). */
    public String imagenUrl() {
        return "/images/" + imagen;
    }
}
