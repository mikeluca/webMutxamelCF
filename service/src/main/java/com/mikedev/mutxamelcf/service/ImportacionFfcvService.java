package com.mikedev.mutxamelcf.service;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.AsignacionEquipoFfcv;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;

/**
 * Importación semanal de partidos desde el CSV de la intranet de la FFCV,
 * en dos pasos: previsualizar (no escribe nada) y confirmar.
 */
public interface ImportacionFfcvService {

    /**
     * Parsea el archivo y resuelve cada fila contra la base de datos sin
     * modificar nada.
     *
     * @throws IllegalArgumentException si el archivo está vacío o no
     *                                  contiene ningún partido ni error legible.
     */
    PrevisualizacionFfcv previsualizar(byte[] archivo);

    /**
     * Guarda en una transacción las relaciones nuevas, crea/actualiza los
     * partidos y crea/borra los descansos del fin de semana. Los datos de
     * cada fila se toman de la previsualización, pero el estado de cada una
     * se recalcula en el servidor.
     *
     * @param usuario usuario del panel que confirma (solo para el log)
     */
    ResultadoImportacionFfcv confirmar(
            String usuario,
            PrevisualizacionFfcv previsualizacion,
            List<AsignacionEquipoFfcv> asignaciones);

    /**
     * Si ya hay una importación confirmada cuyo fin de semana incluye el
     * sábado indicado (partidos de origen FFCV o descansos ese fin de semana).
     */
    boolean hayImportacionParaFinDeSemana(LocalDate sabado);

}
