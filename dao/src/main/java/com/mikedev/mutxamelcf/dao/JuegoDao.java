package com.mikedev.mutxamelcf.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Perfiles y puntuaciones del juego de Mutxigol. Un perfil por cuenta (JUEGO_PERFILES); el ranking solo cuenta
 * perfiles con alias, no ocultos y de cuentas activas, con su mejor marca. Los intervalos de fechas son
 * [desde, hasta): {@code null} es sin límite.
 */
public interface JuegoDao {

    /** El perfil de una cuenta (el alias es null mientras no haya elegido uno). */
    record Perfil(long usuarioId, String alias, String equipacion, boolean oculto) {
    }

    /**
     * Una marca: los puntos, el tiempo de la partida y cuándo se hizo. La que cuenta es la de más puntos (todos los
     * juegos menos PAREJAS) o la de menos tiempo (PAREJAS); ver {@link #JUEGO_DE_TIEMPO}.
     */
    record Marca(int puntos, int duracionMs, LocalDateTime fecha) {
    }

    /** El juego cuya marca es el tiempo (menos es mejor); en los demás la marca son los puntos (más es mejor). */
    String JUEGO_DE_TIEMPO = "PAREJAS";

    /** Una fila del ranking (la posición la pone quien lo pide). */
    record FilaRanking(long usuarioId, String alias, int puntos, int duracionMs, LocalDateTime fecha) {
    }

    /** Una partida guardada. */
    record Puntuacion(long id, String juego, int puntos, int duracionMs, LocalDateTime fecha) {
    }

    /** Un perfil para moderar, con la cuenta (nombre y email: solo se enseñan en el panel y a ADMIN_APP). En Parejas la mejor marca es
     * el menor tiempo en ms (0 si no ha jugado); en los demás, los mejores puntos. */
    record PerfilAdmin(long usuarioId, String alias, String equipacion, boolean oculto, String nombre, String email,
            LocalDateTime fechaAlta, int partidas, int mejorAtrapa, int mejorRegatea, int mejorCabezazos,
            int mejorParejasMs) {
    }

    Optional<Perfil> obtenerPerfil(long usuarioId);

    /** Crea el perfil de la cuenta (el alias puede ser null). */
    void crearPerfil(long usuarioId, String alias, String aliasNorm, String equipacion, LocalDateTime ahora);

    /** Cambia alias y equipación del perfil; un valor null deja ese dato como está. */
    void actualizarPerfil(long usuarioId, String alias, String aliasNorm, String equipacion, LocalDateTime ahora);

    void actualizarOculto(long usuarioId, boolean oculto, LocalDateTime ahora);

    /** Hay otro perfil (distinto de {@code exceptoUsuarioId}) con ese alias normalizado. */
    boolean existeAlias(String aliasNorm, long exceptoUsuarioId);

    void insertarPuntuacion(long usuarioId, String juego, int puntos, int duracionMs, LocalDateTime fecha);

    /** La mejor marca de la cuenta en ese juego (la más antigua si empata), oculta o no. */
    Optional<Marca> mejorMarca(long usuarioId, String juego, LocalDateTime desde, LocalDateTime hasta);

    /** Los {@code limite} mejores (una fila por cuenta, con su mejor marca), de mejor a peor. */
    List<FilaRanking> ranking(String juego, LocalDateTime desde, LocalDateTime hasta, int limite);

    /**
     * Cuántas cuentas del ranking quedan por delante de esa marca (1 + esto es la posición). Desempata la marca más
     * antigua y luego el id de cuenta, igual que {@link #ranking}.
     */
    int cuentasPorDelante(String juego, LocalDateTime desde, LocalDateTime hasta, long usuarioId, Marca marca);

    /** Envíos de la cuenta desde ese instante. */
    int contarPuntuacionesDesde(long usuarioId, LocalDateTime desde);

    /** El instante de la última partida guardada de la cuenta. */
    Optional<LocalDateTime> ultimaPuntuacion(long usuarioId);

    // ---- Moderación ----

    /** Perfiles (con alias o sin él) cuyo alias normalizado contiene el texto (vacío = todos), con tope. */
    List<PerfilAdmin> listarPerfilesAdmin(String aliasNormContiene, int limite);

    Optional<PerfilAdmin> obtenerPerfilAdmin(long usuarioId);

    /** Las últimas partidas de la cuenta (la más reciente primero). */
    List<Puntuacion> ultimasPuntuaciones(long usuarioId, int limite);

    /** Borra una partida; devuelve las filas borradas (0 si no existía). */
    int borrarPuntuacion(long puntuacionId);

    /** Borra todas las partidas de la cuenta; devuelve cuántas. */
    int borrarPuntuacionesDeUsuario(long usuarioId);

    /** Borra el perfil y las partidas de la cuenta (al eliminar la cuenta). */
    void eliminarTodoDeUsuario(long usuarioId);

    // ---- Escudos para Parejas ----

    /** Un club rival con escudo (sin el PNG): para elegir las fichas de Parejas. */
    record ClubConEscudo(long id, String nombre, Integer codigoFfcv, long escudoVersion) {
    }

    /** Todos los clubes rivales que tienen escudo. */
    List<ClubConEscudo> clubesConEscudo();

    /**
     * Ids de los clubes que salen en las clasificaciones de la temporada activa de los equipos "Primer Equipo" y
     * "Filial" (por su código de club de la FFCV), sin nuestras propias filas.
     */
    java.util.Set<Long> clubesDeLasClasificacionesActuales();
}
