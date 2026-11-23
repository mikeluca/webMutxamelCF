package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.ClubRival;
import com.mikedev.mutxamelcf.model.ClubRivalFila;
import com.mikedev.mutxamelcf.model.ResumenGeneracionClubes;

/** Clubes rivales y sus escudos. Nunca se crea un club rival para nuestro propio club. */
public interface ClubRivalService {

    /**
     * Asegura el club rival de una fila de clasificación y devuelve su ID, o null si la fila es de nuestro club (por
     * su código, por ser {@code esNuestro} o por su nombre) o no tiene nombre. Con código: el club con ese código
     * (añade el alias si falta), si no el del alias (y le pone el código), si no lo crea. Un club creado antes por
     * nombre que resulta ser el mismo del código se une al que ya tenía el código. Sin código, por alias.
     */
    Long asegurarDeClasificacion(Integer codigoClub, String nombreEquipo, boolean esNuestro);

    /** Igual, para un nombre de rival de un partido (sin código). Null si es de nuestro club o está vacío. */
    Long asegurarPorNombre(String rival);

    /**
     * true si para este nombre de equipo (sin código de club) habría que crear un club rival al asegurarlo: no es de
     * nuestro club, no está vacío y no existe ya un club con ese alias ni con ese nombre. No escribe nada (para las
     * vistas previas).
     */
    boolean necesitaClubNuevo(String nombreEquipo);

    /** Enlaza el partido con el club de su rival (null si el rival es nuestro o no tiene nombre). */
    void enlazarPartido(Long partidoId, String rival);

    /** Crea los clubes que falten con las clasificaciones de la temporada activa y los partidos, e enlaza partidos. */
    ResumenGeneracionClubes generar();

    List<ClubRivalFila> listar();

    /** Cuántos clubes tienen escudo y cuántos hay. */
    int[] contarConEscudo();

    ClubRival obtener(Long id);

    /** Valida (PNG/JPG, máximo 2 MB), reduce a 256 px y guarda el PNG. */
    void subirEscudo(Long id, byte[] archivo);

    void quitarEscudo(Long id);

    void renombrar(Long id, String nombre);

    /** Pasa alias, partidos y código FFCV de {@code origen} a {@code destino} y borra el origen. */
    void unir(Long origenId, Long destinoId);

    /** Solo si no tiene partidos ni aparece en clasificaciones. */
    void borrar(Long id);

    /** El PNG del club, o null si no existe o no tiene escudo. */
    byte[] obtenerEscudo(Long id);
}
