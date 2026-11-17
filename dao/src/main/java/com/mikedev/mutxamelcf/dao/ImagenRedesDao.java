package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.ImagenRedes;
import com.mikedev.mutxamelcf.model.PartidoImagen;

/** Imágenes para redes sociales (IMAGENES_REDES) y los partidos que las alimentan. */
public interface ImagenRedesDao {

    /** Los partidos del rango con su equipo (modalidad de la temporada activa), por orden del club. */
    List<PartidoImagen> partidosEntre(LocalDate desde, LocalDate hasta);

    /** El PNG del escudo del club rival, o null si no existe o no tiene. */
    byte[] escudoRival(Long clubRivalId);

    /** Borra todas las imágenes de ese tipo y fin de semana (de cualquier modalidad). */
    void borrar(String tipo, LocalDate desde);

    /** La versión más alta guardada para ese tipo y fin de semana (0 si no hay). */
    int versionMaxima(String tipo, LocalDate desde);

    void insertar(String tipo, String modalidad, int pagina, int totalPaginas, LocalDate desde, LocalDate hasta,
            byte[] png, LocalDateTime fechaGeneracion, int version);

    /** Las imágenes del fin de semana (sin el PNG), por tipo, modalidad y página. */
    List<ImagenRedes> listar(LocalDate desde);

    /** El PNG, o null si no existe. */
    byte[] obtenerPng(Long id);
}
