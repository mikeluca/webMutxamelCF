package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PrevisualizacionVeteranos;
import com.mikedev.mutxamelcf.model.ResultadoImportacionVeteranos;

/**
 * Importación de la clasificación de la Liga Veteranos Ciudad de Alicante (LVCA) para el equipo Veteranos, en dos pasos:
 * previsualizar (no escribe nada) y confirmar. La fuente es la página https://ligaveteranosalicante.es/scoretable/
 * guardada desde el navegador, que sube siempre una persona (nunca se descarga desde el servidor).
 */
public interface ImportacionVeteranosService {

    /** El nombre del equipo del club al que se asignan las clasificaciones (se busca como "Primer Equipo"). */
    String EQUIPO_VETERANOS = "Veteranos";

    /**
     * Lee la página y dice qué se importaría: una clasificación por cada competición en la que aparece el Mutxamel,
     * con su tabla y los clubes rivales que habría que crear.
     *
     * @throws IllegalArgumentException si el archivo no es esa página, una tabla del Mutxamel está incompleta, el
     *                                  Mutxamel no aparece en ninguna clasificación o no existe el equipo Veteranos
     */
    PrevisualizacionVeteranos previsualizar(byte[] archivo);

    /**
     * Guarda la vista previa: por cada competición sustituye la clasificación que tuviera el equipo Veteranos en esa
     * competición (las demás no se tocan) y crea los clubes rivales que falten, sin escudo.
     *
     * @param usuario quien confirma (solo para el log)
     */
    ResultadoImportacionVeteranos confirmar(String usuario, PrevisualizacionVeteranos previsualizacion);
}
