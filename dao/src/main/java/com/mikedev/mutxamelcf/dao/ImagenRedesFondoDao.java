package com.mikedev.mutxamelcf.dao;

import com.mikedev.mutxamelcf.model.FondoCartel;

/** Fondos de los carteles para redes sociales editables desde el panel (IMAGENES_REDES_FONDOS). */
public interface ImagenRedesFondoDao {

    /** El fondo guardado de ese cartel, o null si no hay (se usa el de por defecto). */
    FondoCartel obtener(String cartel);

    /** Guarda el fondo de ese cartel, sustituyendo el anterior si lo había. */
    void guardar(String cartel, byte[] imagen, String tipoContenido, int ancho, int alto, String usuario);

    /** Borra el fondo guardado (vuelve el de por defecto). Devuelve si había alguno. */
    boolean borrar(String cartel);
}
