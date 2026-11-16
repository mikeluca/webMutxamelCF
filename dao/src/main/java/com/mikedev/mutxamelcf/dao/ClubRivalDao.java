package com.mikedev.mutxamelcf.dao;

import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.model.ClubRival;

/** Clubes rivales (CLUBES_RIVALES) y sus alias normalizados (CLUBES_RIVALES_ALIAS). */
public interface ClubRivalDao {

    /** Crea el club (sin escudo) y devuelve su ID. {@code codigoFfcv} puede ser null. */
    Long crear(String nombre, Integer codigoFfcv);

    ClubRival obtenerPorId(Long id);

    ClubRival obtenerPorCodigo(int codigoFfcv);

    /** El club al que pertenece un nombre ya normalizado, o null. */
    ClubRival obtenerPorAlias(String nombreNormalizado);

    /** El club con ese NOMBRE exacto (el nombre es único), o null. */
    ClubRival obtenerPorNombre(String nombre);

    /** Todos, por nombre. No lee el escudo. */
    List<ClubRival> listar();

    /** Añade el alias si no existe; true si lo ha añadido. Un alias solo puede ser de un club. */
    boolean anadirAlias(Long clubId, String nombreNormalizado);

    List<String> listarAlias(Long clubId);

    /** Todos los alias: nombre normalizado → club. */
    Map<String, Long> mapaAlias();

    /** Alias de cada club, ordenados. */
    Map<Long, List<String>> aliasPorClub();

    void ponerCodigo(Long clubId, Integer codigoFfcv);

    void renombrar(Long clubId, String nombre);

    /** Guarda el PNG, sube ESCUDO_VERSION y pone FECHA_ACTUALIZACION. */
    void guardarEscudo(Long clubId, byte[] png);

    /** Quita el escudo y sube ESCUDO_VERSION (la app deja de mostrarlo). */
    void quitarEscudo(Long clubId);

    /** Copia el escudo de un club a otro sin leerlo en Java y sube la versión del destino. */
    void copiarEscudo(Long origenId, Long destinoId);

    /** El PNG, o null si el club no existe o no tiene. */
    byte[] obtenerEscudo(Long clubId);

    /** Pasa los alias del origen al destino. */
    void moverAlias(Long origenId, Long destinoId);

    /** Pasa los partidos del origen al destino. */
    void moverPartidos(Long origenId, Long destinoId);

    int contarPartidos(Long clubId);

    /** Filas de CLASIFICACIONES (de cualquier temporada) con el código de club de este club. */
    int contarClasificaciones(Long clubId);

    /** Borra el club y sus alias. No toca partidos: hay que desenlazarlos antes. */
    void borrar(Long clubId);

    /** Nº de partidos por club. */
    Map<Long, Integer> contarPartidosPorClub();

    /** Nombres de los equipos nuestros que han jugado contra cada club (por sus partidos), sin repetir. */
    Map<Long, List<String>> equiposPorClubDePartidos();
}
