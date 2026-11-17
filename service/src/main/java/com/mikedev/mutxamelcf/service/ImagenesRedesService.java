package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;

import com.mikedev.mutxamelcf.model.ImagenesRedesResponse;

/**
 * Imágenes para redes sociales (previa y resultados del fin de semana). Los métodos que reciben un usuario son solo
 * para COORDINADOR y ADMIN_APP ({@link SecurityException} al resto).
 */
public interface ImagenesRedesService {

    String TIPO_PREVIA = "PREVIA";
    String TIPO_RESULTADOS = "RESULTADOS";

    /** Valor de data["tipo"] y data["pantalla"] del push que avisa de que están listas. */
    String PUSH_IMAGENES_REDES = "IMAGENES_REDES";

    /** Las imágenes del fin de semana de {@code fecha} (por defecto hoy; mismo criterio que el panel Fin de semana). */
    ImagenesRedesResponse listar(Long usuarioAppId, LocalDate fecha);

    /** El PNG de una imagen. {@link IllegalArgumentException} si no existe. */
    byte[] descargar(Long usuarioAppId, Long imagenId);

    /** Regenera a mano las imágenes de ese tipo y fin de semana, sin avisos. */
    ImagenesRedesResponse regenerar(Long usuarioAppId, String tipo, LocalDate fecha);

    /**
     * Genera las imágenes de ese tipo para el fin de semana que empieza el viernes {@code viernes} y avisa a
     * coordinación (bandeja + push). Lo usan los schedulers. Devuelve cuántas imágenes se han generado.
     */
    int generarYAvisar(String tipo, LocalDate viernes);
}
