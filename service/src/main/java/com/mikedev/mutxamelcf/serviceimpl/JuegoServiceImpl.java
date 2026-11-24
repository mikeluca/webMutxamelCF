package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.JuegoDao;
import com.mikedev.mutxamelcf.dao.JuegoDao.Marca;
import com.mikedev.mutxamelcf.dao.JuegoDao.Perfil;
import com.mikedev.mutxamelcf.service.JuegoConflictoException;
import com.mikedev.mutxamelcf.service.JuegoLimiteEnviosException;
import com.mikedev.mutxamelcf.service.JuegoService;

@Service
public class JuegoServiceImpl implements JuegoService {

    private static final Logger logger = LoggerFactory.getLogger(JuegoServiceImpl.class);

    // ------------------------------------------------------------------
    // Reglas de plausibilidad (docs/api-app-juego.md): una puntuación que no las cumple no se guarda (400).
    // ------------------------------------------------------------------

    /** El ranking enseña los 50 mejores. */
    public static final int TAMANO_RANKING = 50;

    /** Puntos máximos de una partida (y la columna PUNTOS admite hasta 999). */
    public static final int PUNTOS_MAXIMOS = 999;

    /** Duración mínima de una partida: 3 segundos. */
    public static final int DURACION_MINIMA_MS = 3_000;

    /** Duración máxima de una partida: 2 horas. */
    public static final int DURACION_MAXIMA_MS = 2 * 60 * 60 * 1000;

    /** Margen sobre el ritmo máximo de cada juego (20 %), por si la partida es algo mejor de lo previsto. */
    public static final double MARGEN_RITMO = 1.2;

    /** Ritmo máximo por segundo: ATRAPA 6 (balón dorado de 5 puntos y varios balones a la vez). */
    public static final double RITMO_MAXIMO_ATRAPA = 6;

    /** Ritmo máximo por segundo: REGATEA 4. */
    public static final double RITMO_MAXIMO_REGATEA = 4;

    /** Ritmo máximo por segundo: CABEZAZOS 1 (un punto por tubería). */
    public static final double RITMO_MAXIMO_CABEZAZOS = 1;

    /** PAREJAS: solo partidas completas, las 8 parejas del tablero 4×4 (los PUNTOS son siempre 8). */
    public static final int PUNTOS_PAREJAS = 8;

    /** PAREJAS: tiempo mínimo creíble para completar el tablero, 8 segundos. */
    public static final int DURACION_MINIMA_PAREJAS_MS = 8_000;

    // ------------------------------------------------------------------
    // Límite de envíos por cuenta
    // ------------------------------------------------------------------

    /** Al menos 5 segundos entre dos envíos de la misma cuenta. */
    public static final Duration INTERVALO_MINIMO_ENTRE_ENVIOS = Duration.ofSeconds(5);

    /** Como mucho 300 envíos al día (día natural en hora de Madrid) por cuenta. */
    public static final int ENVIOS_MAXIMOS_AL_DIA = 300;

    static final String MENSAJE_SIN_ALIAS = "Elige un alias para guardar tu puntuación y salir en el ranking";
    static final String MENSAJE_ALIAS_EN_USO = "Ese alias ya está en uso";

    static final Set<String> EQUIPACIONES = Set.of("AZUL", "ROSA");

    /** Los juegos válidos y el orden en que salen en las respuestas. */
    static final List<String> ORDEN_JUEGOS = List.of("ATRAPA", "REGATEA", "CABEZAZOS", JuegoDao.JUEGO_DE_TIEMPO);
    static final Set<String> JUEGOS = Set.copyOf(ORDEN_JUEGOS);

    private final JuegoDao juegoDao;
    private final AliasJuego aliasJuego;
    private final Clock clock;
    private final int codigoClubPropio;
    private final Random azar;

    @Autowired
    public JuegoServiceImpl(JuegoDao juegoDao, AliasJuego aliasJuego, Clock clock,
            @Value("${ffcv.club.codigo:3010}") int codigoClubPropio) {
        this(juegoDao, aliasJuego, clock, codigoClubPropio, new Random());
    }

    /** Con el azar a medida (los tests eligen escudos con una semilla fija). */
    JuegoServiceImpl(JuegoDao juegoDao, AliasJuego aliasJuego, Clock clock, int codigoClubPropio, Random azar) {
        this.juegoDao = juegoDao;
        this.aliasJuego = aliasJuego;
        this.clock = clock;
        this.codigoClubPropio = codigoClubPropio;
        this.azar = azar;
    }

    /** Qué mide cada juego: PAREJAS el tiempo (menos es mejor), los demás los puntos (más es mejor). */
    static String tipoDeMarca(String juego) {
        return JuegoDao.JUEGO_DE_TIEMPO.equals(juego) ? MARCA_TIEMPO : MARCA_PUNTOS;
    }

    /** Si la partida {@code nueva} mejora a la {@code anterior} (sin anterior, siempre que cuente: ver el uso). */
    private static boolean mejora(String juego, int puntos, int duracionMs, Marca anterior) {

        if (JuegoDao.JUEGO_DE_TIEMPO.equals(juego)) {
            return anterior == null || duracionMs < anterior.duracionMs();
        }

        return puntos > (anterior == null ? 0 : anterior.puntos());
    }

    // ------------------------------------------------------------------
    // Periodos
    // ------------------------------------------------------------------

    /** Lunes 00:00 (hora de Madrid) de la semana que contiene a {@code ahora}. */
    static LocalDateTime inicioDeSemana(LocalDateTime ahora) {
        return ahora.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    private static String juegoValido(String juego) {
        String valor = juego == null ? "" : juego.trim().toUpperCase(Locale.ROOT);

        if (!JUEGOS.contains(valor)) {
            throw new IllegalArgumentException("El juego tiene que ser ATRAPA, REGATEA, CABEZAZOS o PAREJAS");
        }

        return valor;
    }

    // ------------------------------------------------------------------
    // Ranking y perfil
    // ------------------------------------------------------------------

    @Override
    public List<FilaRanking> ranking(String juego, String periodo) {

        String juegoValido = juegoValido(juego);
        String p = periodo == null || periodo.isBlank() ? "SEMANA" : periodo.trim().toUpperCase(Locale.ROOT);

        if (!p.equals("SEMANA") && !p.equals("HISTORICO")) {
            throw new IllegalArgumentException("El periodo tiene que ser SEMANA o HISTORICO");
        }

        LocalDateTime desde = p.equals("SEMANA") ? inicioDeSemana(ahora()) : null;
        LocalDateTime hasta = p.equals("SEMANA") ? desde.plusWeeks(1) : null;

        List<JuegoDao.FilaRanking> filas = juegoDao.ranking(juegoValido, desde, hasta, TAMANO_RANKING);
        java.util.ArrayList<FilaRanking> resultado = new java.util.ArrayList<>();

        for (int i = 0; i < filas.size(); i++) {
            JuegoDao.FilaRanking f = filas.get(i);
            resultado.add(new FilaRanking(i + 1, f.alias(), tipoDeMarca(juegoValido), f.puntos(), f.duracionMs(),
                    f.fecha()));
        }

        return resultado;
    }

    private MarcaConPosicion marcaConPosicion(long usuarioId, String juego, LocalDateTime desde, LocalDateTime hasta,
            boolean oculto) {

        Optional<Marca> marca = juegoDao.mejorMarca(usuarioId, juego, desde, hasta);

        if (marca.isEmpty()) {
            return null;
        }

        Integer posicion = oculto ? null
                : juegoDao.cuentasPorDelante(juego, desde, hasta, usuarioId, marca.get()) + 1;

        return new MarcaConPosicion(marca.get().puntos(), marca.get().duracionMs(), marca.get().fecha(), posicion);
    }

    @Override
    public PerfilJuego perfil(int usuarioId) {

        Optional<Perfil> perfil = juegoDao.obtenerPerfil(usuarioId);
        boolean oculto = perfil.map(Perfil::oculto).orElse(false);

        LocalDateTime semana = inicioDeSemana(ahora());
        Map<String, MarcasJuego> juegos = new LinkedHashMap<>();

        for (String juego : ORDEN_JUEGOS) {
            juegos.put(juego,
                    new MarcasJuego(tipoDeMarca(juego),
                            marcaConPosicion(usuarioId, juego, semana, semana.plusWeeks(1), oculto),
                            marcaConPosicion(usuarioId, juego, null, null, oculto)));
        }

        return new PerfilJuego(perfil.map(Perfil::alias).orElse(null), perfil.map(Perfil::equipacion).orElse("AZUL"),
                oculto, juegos);
    }

    @Override
    @Transactional
    public PerfilJuego actualizarPerfil(int usuarioId, String alias, String equipacion) {

        boolean cambiaAlias = alias != null;
        boolean cambiaEquipacion = equipacion != null;

        if (!cambiaAlias && !cambiaEquipacion) {
            throw new IllegalArgumentException("Indica el alias o la equipación");
        }

        String aliasLimpio = cambiaAlias ? aliasJuego.validar(alias) : null;
        String equipacionValida = cambiaEquipacion ? equipacionValida(equipacion) : null;
        String aliasNorm = cambiaAlias ? AliasJuego.normalizar(aliasLimpio) : null;

        if (cambiaAlias && juegoDao.existeAlias(aliasNorm, usuarioId)) {
            throw new JuegoConflictoException(JuegoConflictoException.ALIAS_EN_USO, MENSAJE_ALIAS_EN_USO);
        }

        try {
            if (juegoDao.obtenerPerfil(usuarioId).isPresent()) {
                juegoDao.actualizarPerfil(usuarioId, aliasLimpio, aliasNorm, equipacionValida, ahora());
            } else {
                juegoDao.crearPerfil(usuarioId, aliasLimpio, aliasNorm,
                        equipacionValida == null ? "AZUL" : equipacionValida, ahora());
            }
        } catch (DataIntegrityViolationException e) {
            // Dos cuentas eligiendo el mismo alias a la vez: gana la primera (UQ_JUEGO_PERFIL_ALIAS).
            throw new JuegoConflictoException(JuegoConflictoException.ALIAS_EN_USO, MENSAJE_ALIAS_EN_USO);
        }

        return perfil(usuarioId);
    }

    private static String equipacionValida(String equipacion) {
        String valor = equipacion.trim().toUpperCase(Locale.ROOT);

        if (!EQUIPACIONES.contains(valor)) {
            throw new IllegalArgumentException("La equipación tiene que ser AZUL o ROSA");
        }

        return valor;
    }

    // ------------------------------------------------------------------
    // Puntuaciones
    // ------------------------------------------------------------------

    /** Los puntos máximos creíbles de una partida de esa duración (con el margen); como mucho 999. */
    static int puntosMaximosPlausibles(String juego, int duracionMs) {

        double ritmo = switch (juego) {
        case "REGATEA" -> RITMO_MAXIMO_REGATEA;
        case "CABEZAZOS" -> RITMO_MAXIMO_CABEZAZOS;
        default -> RITMO_MAXIMO_ATRAPA;
        };

        // El 1e-9 evita que un ritmo justo se quede un punto corto por redondeo en los casos justos.
        double maximo = Math.floor(ritmo * (duracionMs / 1000.0) * MARGEN_RITMO + 1e-9);

        return (int) Math.min(maximo, PUNTOS_MAXIMOS);
    }

    @Override
    @Transactional
    public ResultadoPuntuacion guardarPuntuacion(int usuarioId, String juego, Integer puntos, Integer duracionMs) {

        String juegoValido = juegoValido(juego);

        Optional<Perfil> perfil = juegoDao.obtenerPerfil(usuarioId);

        if (perfil.isEmpty() || perfil.get().alias() == null) {
            throw new JuegoConflictoException(JuegoConflictoException.ALIAS_REQUERIDO, MENSAJE_SIN_ALIAS);
        }

        if (puntos == null || duracionMs == null) {
            throw new IllegalArgumentException("Faltan los puntos o la duración de la partida");
        }

        if (puntos < 0 || puntos > PUNTOS_MAXIMOS) {
            throw new IllegalArgumentException("Los puntos tienen que estar entre 0 y " + PUNTOS_MAXIMOS);
        }

        if (duracionMs < DURACION_MINIMA_MS || duracionMs > DURACION_MAXIMA_MS) {
            throw new IllegalArgumentException("La duración de la partida no es válida");
        }

        if (JuegoDao.JUEGO_DE_TIEMPO.equals(juegoValido)) {
            // PAREJAS puntúa por tiempo y solo se guarda la partida completa: siempre las 8 parejas.
            if (puntos != PUNTOS_PAREJAS || duracionMs < DURACION_MINIMA_PAREJAS_MS) {
                logger.info("Partida de Parejas rechazada por implausible: usuarioAppId={}, puntos={}, duracionMs={}",
                        usuarioId, puntos, duracionMs);
                throw new IllegalArgumentException(puntos != PUNTOS_PAREJAS
                        ? "En Parejas solo se guardan partidas completas (" + PUNTOS_PAREJAS + " parejas)"
                        : "La puntuación no es válida para esa duración");
            }
        } else if (puntos > puntosMaximosPlausibles(juegoValido, duracionMs)) {
            logger.info("Puntuación del juego rechazada por implausible: usuarioAppId={}, juego={}, puntos={}, "
                    + "duracionMs={}", usuarioId, juegoValido, puntos, duracionMs);
            throw new IllegalArgumentException("La puntuación no es válida para esa duración");
        }

        LocalDateTime ahora = ahora();
        comprobarLimiteDeEnvios(usuarioId, ahora);

        LocalDateTime semana = inicioDeSemana(ahora);
        LocalDateTime siguiente = semana.plusWeeks(1);
        boolean oculto = perfil.get().oculto();

        Marca mejorSemana = juegoDao.mejorMarca(usuarioId, juegoValido, semana, siguiente).orElse(null);
        Marca mejorHistorico = juegoDao.mejorMarca(usuarioId, juegoValido, null, null).orElse(null);

        juegoDao.insertarPuntuacion(usuarioId, juegoValido, puntos, duracionMs, ahora);

        MarcaConPosicion enSemana = marcaConPosicion(usuarioId, juegoValido, semana, siguiente, oculto);
        MarcaConPosicion enHistorico = marcaConPosicion(usuarioId, juegoValido, null, null, oculto);

        return new ResultadoPuntuacion(juegoValido, tipoDeMarca(juegoValido), puntos, duracionMs,
                mejora(juegoValido, puntos, duracionMs, mejorSemana),
                mejora(juegoValido, puntos, duracionMs, mejorHistorico),
                enSemana == null ? null : enSemana.posicion(), enHistorico == null ? null : enHistorico.posicion());
    }

    private void comprobarLimiteDeEnvios(int usuarioId, LocalDateTime ahora) {

        Optional<LocalDateTime> ultima = juegoDao.ultimaPuntuacion(usuarioId);

        if (ultima.isPresent() && Duration.between(ultima.get(), ahora).compareTo(INTERVALO_MINIMO_ENTRE_ENVIOS) < 0) {
            throw new JuegoLimiteEnviosException("Vas muy rápido: espera unos segundos antes de enviar otra partida");
        }

        if (juegoDao.contarPuntuacionesDesde(usuarioId, ahora.toLocalDate().atStartOfDay()) >= ENVIOS_MAXIMOS_AL_DIA) {
            throw new JuegoLimiteEnviosException("Has llegado al máximo de partidas guardadas de hoy: vuelve mañana");
        }
    }

    // ------------------------------------------------------------------
    // Escudos para Parejas
    // ------------------------------------------------------------------

    @Override
    public List<EscudoJuego> escudos(Integer cantidad) {

        int pedidos = cantidad == null ? ESCUDOS_POR_DEFECTO : cantidad;

        if (pedidos < ESCUDOS_MINIMO || pedidos > ESCUDOS_MAXIMO) {
            throw new IllegalArgumentException(
                    "La cantidad tiene que estar entre " + ESCUDOS_MINIMO + " y " + ESCUDOS_MAXIMO);
        }

        // Nunca nuestro club (ni por su código de la FFCV ni por su nombre).
        List<JuegoDao.ClubConEscudo> candidatos = juegoDao.clubesConEscudo().stream()
                .filter(c -> c.codigoFfcv() == null || c.codigoFfcv() != codigoClubPropio)
                .filter(c -> !NombreClub.esPropio(c.nombre()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        Set<Long> actuales = juegoDao.clubesDeLasClasificacionesActuales();

        // Primero los de las clasificaciones de esta temporada; si no llegan, se completa con los demás.
        List<JuegoDao.ClubConEscudo> prioritarios = new java.util.ArrayList<>(
                candidatos.stream().filter(c -> actuales.contains(c.id())).toList());
        List<JuegoDao.ClubConEscudo> resto = new java.util.ArrayList<>(
                candidatos.stream().filter(c -> !actuales.contains(c.id())).toList());

        java.util.Collections.shuffle(prioritarios, azar);
        java.util.Collections.shuffle(resto, azar);

        List<JuegoDao.ClubConEscudo> elegidos = new java.util.ArrayList<>(prioritarios);

        elegidos.addAll(resto);

        return elegidos.stream().limit(pedidos)
                .map(c -> new EscudoJuego(c.id(), c.nombre(), EscudosRivales.url(c.id(), c.escudoVersion()))).toList();
    }

    // ------------------------------------------------------------------
    // Moderación
    // ------------------------------------------------------------------

    private static final int MAXIMO_PERFILES_ADMIN = 500;
    private static final int MAXIMO_PUNTUACIONES_ADMIN = 100;

    @Override
    public List<JuegoDao.PerfilAdmin> listarPerfilesAdmin(String alias) {
        String filtro = alias == null || alias.isBlank() ? "" : AliasJuego.normalizar(alias);

        return juegoDao.listarPerfilesAdmin(filtro, MAXIMO_PERFILES_ADMIN);
    }

    @Override
    public JuegoDao.PerfilAdmin obtenerPerfilAdmin(long usuarioId) {
        return juegoDao.obtenerPerfilAdmin(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Esa cuenta no tiene perfil en el juego"));
    }

    @Override
    public List<JuegoDao.Puntuacion> puntuacionesAdmin(long usuarioId) {
        obtenerPerfilAdmin(usuarioId);

        return juegoDao.ultimasPuntuaciones(usuarioId, MAXIMO_PUNTUACIONES_ADMIN);
    }

    @Override
    @Transactional
    public JuegoDao.PerfilAdmin cambiarAliasAdmin(long usuarioId, String alias) {

        obtenerPerfilAdmin(usuarioId);

        String limpio = aliasJuego.validar(alias);
        String norm = AliasJuego.normalizar(limpio);

        if (juegoDao.existeAlias(norm, usuarioId)) {
            throw new JuegoConflictoException(JuegoConflictoException.ALIAS_EN_USO, MENSAJE_ALIAS_EN_USO);
        }

        try {
            juegoDao.actualizarPerfil(usuarioId, limpio, norm, null, ahora());
        } catch (DataIntegrityViolationException e) {
            throw new JuegoConflictoException(JuegoConflictoException.ALIAS_EN_USO, MENSAJE_ALIAS_EN_USO);
        }

        logger.info("Alias del juego cambiado por un administrador: usuarioAppId={}", usuarioId);

        return obtenerPerfilAdmin(usuarioId);
    }

    @Override
    @Transactional
    public JuegoDao.PerfilAdmin ocultarAdmin(long usuarioId, boolean oculto) {

        obtenerPerfilAdmin(usuarioId);
        juegoDao.actualizarOculto(usuarioId, oculto, ahora());

        logger.info("Perfil del juego {} por un administrador: usuarioAppId={}", oculto ? "ocultado" : "mostrado",
                usuarioId);

        return obtenerPerfilAdmin(usuarioId);
    }

    @Override
    @Transactional
    public void borrarPuntuacionAdmin(long puntuacionId) {

        if (juegoDao.borrarPuntuacion(puntuacionId) == 0) {
            throw new NoSuchElementException("Esa puntuación no existe");
        }

        logger.info("Puntuación del juego borrada por un administrador: puntuacionId={}", puntuacionId);
    }

    @Override
    @Transactional
    public int borrarPuntuacionesAdmin(long usuarioId) {

        obtenerPerfilAdmin(usuarioId);

        int borradas = juegoDao.borrarPuntuacionesDeUsuario(usuarioId);

        logger.info("Puntuaciones del juego borradas por un administrador: usuarioAppId={}, total={}", usuarioId,
                borradas);

        return borradas;
    }
}
