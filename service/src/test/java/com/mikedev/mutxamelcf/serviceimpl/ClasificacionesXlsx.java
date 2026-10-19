package com.mikedev.mutxamelcf.serviceimpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Genera un ListadoClasificaciones.xlsx pequeño con la misma pinta que el real:
 * hoja "Hoja1", título en la fila 1, cabecera en la 2 con espacio duro delante.
 */
final class ClasificacionesXlsx {

    static final List<String> CABECERA = List.of(
            "Competición", "Grupo", "Equipo", "Letra", "Club", "Delegación", "Jornada", "Posición", "Puntos",
            "Partidos jugados", "Partidos ganados", "Partidos empatados", "Partidos perdidos",
            "Goles a favor", "Goles en contra", "Ult. Jornada Jugada", "Ult. Partido Jugado",
            "Ratio Puntos/Part. Jugados");

    private final List<String> cabecera = new ArrayList<>(CABECERA);
    private final List<Object[]> filas = new ArrayList<>();

    ClasificacionesXlsx sinColumna(String nombre) {
        cabecera.remove(nombre);
        return this;
    }

    /** Los números se pasan como String para simular un Excel que los trae como texto. */
    ClasificacionesXlsx fila(String competicion, String grupo, String equipo, String letra, Object club,
            Object jornada, Object posicion, Object puntos, Object pj, Object pg, Object pe, Object pp,
            Object gf, Object gc, String ultPartido) {

        filas.add(new Object[] { competicion, grupo, equipo, letra, club, "Alacant", jornada, posicion, puntos,
                pj, pg, pe, pp, gf, gc, jornada, ultPartido, "1,50" });
        return this;
    }

    byte[] bytes() {

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = libro.createSheet("Hoja1");
            hoja.createRow(0).createCell(0).setCellValue("LISTADO DE CLASIFICACIONES");

            Row filaCabecera = hoja.createRow(1);
            for (int i = 0; i < cabecera.size(); i++) {
                // El Excel real trae un espacio duro delante de cada cabecera.
                filaCabecera.createCell(i).setCellValue(" " + cabecera.get(i));
            }

            int numeroFila = 2;
            for (Object[] fila : filas) {
                Row row = hoja.createRow(numeroFila++);
                for (int i = 0; i < CABECERA.size(); i++) {
                    int destino = cabecera.indexOf(CABECERA.get(i));
                    if (destino < 0) {
                        continue;
                    }
                    Object valor = fila[i];
                    if (valor instanceof Number numero) {
                        row.createCell(destino).setCellValue(numero.doubleValue());
                    } else if (valor != null) {
                        row.createCell(destino).setCellValue(valor.toString());
                    }
                }
            }

            libro.write(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
