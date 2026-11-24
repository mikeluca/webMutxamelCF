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

    /** Una fila del ranking público: nunca el nombre real, solo el alias. */
    record FilaRanking(int posicion, String alias, int puntos, LocalDateTime fecha) {
    }

    /** Una mejor marca con la posición que ocupa en el ranking (null si el perfil está oculto). */
    record MarcaConPosicion(int puntos, LocalDateTime fecha, Integer posicion) {
    }

    /** La mejor marca de un juego, de la semana y del histórico (null si aún no hay). */
    record MarcasJuego(MarcaConPosicion semana, MarcaConPosicion historico) {
    }

    /** El perfil de la cuenta con sus marcas por juego (PENALTIS, TOQUES, ATRAPA). */
    record PerfilJuego(String alias, String equipacion, boolean oculto, Map<String, MarcasJuego> juegos) {
    }

    /** Lo que devuelve guardar una puntuación. */
    record ResultadoPuntuacion(String juego, int puntos, boolean recordSemana, boolean recordHistorico,
            Integer posicionSemana, Integer posicionHistorico) {
    }

    /** Los 50 mejores de un juego; periodo SEMANA (por defecto) o HISTORICO. 400 si el juego o el periodo no valen. */
    List<FilaRanking> ranking(String juego, String periodo);

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
