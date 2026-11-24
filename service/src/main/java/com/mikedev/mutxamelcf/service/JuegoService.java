package com.mikedev.mutxamelcf.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.dao.JuegoDao;

/**
 * El juego de Mutxigol: ranking público (solo con alias), perfil y puntuaciones de las cuentas de la app y su
 * moderación (panel web y ADMIN_APP desde la app). Ver docs/api-app-juego.md.
 */
public interface JuegoService {

    /** Cómo se mide la marca de un juego: los PUNTOS (más es mejor) o el TIEMPO (PAREJAS: menos es mejor). */
    String MARCA_PUNTOS = "PUNTOS";
    String MARCA_TIEMPO = "TIEMPO";

    /**
     * Una fila del ranking público: nunca el nombre real, solo el alias. {@code marca} dice qué mide el juego (PUNTOS o
     * TIEMPO) y {@code tiempoMs} es el tiempo de esa partida.
     */
    record FilaRanking(int posicion, String alias, String marca, int puntos, int tiempoMs, LocalDateTime fecha) {
    }

    /** Una mejor marca con la posición que ocupa en el ranking (null si el perfil está oculto). */
    record MarcaConPosicion(int puntos, int tiempoMs, LocalDateTime fecha, Integer posicion) {
    }

    /** La mejor marca de un juego (PUNTOS o TIEMPO), de la semana y del histórico (null si aún no hay). */
    record MarcasJuego(String marca, MarcaConPosicion semana, MarcaConPosicion historico) {
    }

    /** El perfil de la cuenta con sus marcas por juego (ATRAPA, CABEZAZOS, PAREJAS). */
    record PerfilJuego(String alias, String equipacion, boolean oculto, Map<String, MarcasJuego> juegos) {
    }

    /** Lo que devuelve guardar una puntuación (los récords, en PAREJAS, son de tiempo: menos es mejor). */
    record ResultadoPuntuacion(String juego, String marca, int puntos, int tiempoMs, boolean recordSemana,
            boolean recordHistorico, Integer posicionSemana, Integer posicionHistorico) {
    }

    /** Los 50 mejores de un juego; periodo SEMANA (por defecto) o HISTORICO. 400 si el juego o el periodo no valen. */
    List<FilaRanking> ranking(String juego, String periodo);

    /** Un club con escudo para las fichas de Parejas: sin datos personales. */
    record EscudoJuego(long id, String nombre, String escudoUrl) {
    }

    /** Cantidad de escudos por defecto (8 parejas del tablero 4×4) y sus límites. */
    int ESCUDOS_POR_DEFECTO = 8;
    int ESCUDOS_MINIMO = 2;
    int ESCUDOS_MAXIMO = 12;

    /**
     * Clubes rivales con escudo, al azar, para las fichas de Parejas. Prioriza los de las clasificaciones de la
     * temporada actual del Primer Equipo y del Filial, nunca nuestro club. {@code cantidad} null = 8; fuera de 2 a 12
     * da 400. Si hay menos clubes con escudo que los pedidos, devuelve los que haya.
     */
    List<EscudoJuego> escudos(Integer cantidad);

    PerfilJuego perfil(int usuarioId);

    /** Cambia alias y/o equipación (al menos uno); crea el perfil si no existía. */
    PerfilJuego actualizarPerfil(int usuarioId, String alias, String equipacion);

    /** Guarda la partida si es plausible; 409 sin alias, 400 si no es plausible, 429 si se pasa del límite. */
    ResultadoPuntuacion guardarPuntuacion(int usuarioId, String juego, Integer puntos, Integer duracionMs);

    // ---- Moderación (quien llama ya ha comprobado que puede: panel SUPER/ADMIN o ADMIN_APP) ----

    List<JuegoDao.PerfilAdmin> listarPerfilesAdmin(String alias);

    JuegoDao.PerfilAdmin obtenerPerfilAdmin(long usuarioId);

    List<JuegoDao.Puntuacion> puntuacionesAdmin(long usuarioId);

    JuegoDao.PerfilAdmin cambiarAliasAdmin(long usuarioId, String alias);

    JuegoDao.PerfilAdmin ocultarAdmin(long usuarioId, boolean oculto);

    void borrarPuntuacionAdmin(long puntuacionId);

    int borrarPuntuacionesAdmin(long usuarioId);
}
