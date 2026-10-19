package com.mikedev.mutxamelcf.service;

import java.util.Collection;

import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;

/**
 * Importación semanal de clasificaciones desde el ListadoClasificaciones.xlsx
 * de la intranet de la FFCV, en dos pasos: previsualizar (no escribe nada) y
 * confirmar.
 */
public interface ImportacionClasificacionesService {

    /**
     * Lee el Excel y, para cada equipo con relación en EQUIPOS_FFCV, busca su
     * grupo y calcula qué cambiaría. No modifica nada.
     *
     * @throws IllegalArgumentException si el archivo no es un .xlsx válido o
     *                                  le falta alguna columna
     */
    PrevisualizacionClasificaciones previsualizar(byte[] archivo);

    /**
     * En una transacción y por cada equipo marcado: borra su clasificación y
     * inserta la tabla del grupo tal como salió en la previsualización (no se
     * vuelve a leer ningún archivo).
     *
     * @param equiposMarcados IDs de los equipos a importar; todos deben estar
     *                        en la previsualización
     * @param usuario         usuario del panel que confirma (solo para el log)
     * @throws IllegalArgumentException si no hay equipos marcados o alguno no
     *                                  pertenece a la previsualización
     */
    ResultadoImportacionClasificaciones confirmar(
            String usuario,
            PrevisualizacionClasificaciones previsualizacion,
            Collection<Long> equiposMarcados);

}
