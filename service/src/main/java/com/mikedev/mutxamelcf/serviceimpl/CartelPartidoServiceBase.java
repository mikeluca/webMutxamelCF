package com.mikedev.mutxamelcf.serviceimpl;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.CartelRedes;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse.TextoPublicacion;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CartelNoDisponibleException;
import com.mikedev.mutxamelcf.service.FondosCartelesService;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;

/**
 * Lo común a los carteles "Próximo partido" (Primer Equipo y Filial): qué partido de su equipo toca (con la regla de
 * saltar los terminados y {@code YA_EMPEZADO} solo en juego), el recorte del campo, el lado local/visitante, la racha,
 * el escudo del rival, el fondo (el de la tabla o el de resources), el texto para la publicación y la validación de los
 * parámetros. Cada cartel solo aporta su equipo, su renderizador y su fondo por defecto.
 */
abstract class CartelPartidoServiceBase {

    static final String NOMBRE_MUTXAMEL = "Mutxamel C.F.";

    private static final int PARTIDOS_RACHA = 5;
    private static final Pattern HORA = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA_TEXTO = DateTimeFormatter.ofPattern("HH:mm");

    static final String LOGO_PRIMERA = "/redes/logos/competiciones/primera-ffcv.png";
    static final String LOGO_SEGONA = "/redes/logos/competiciones/segona-ffcv.png";
    static final String LOGO_COPA = "/redes/logos/competiciones/la-nostra-copa.png";

    private static final String[] DIAS_ES = { "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO",
            "DOMINGO" };
    private static final String[] DIAS_CA = { "DILLUNS", "DIMARTS", "DIMECRES", "DIJOUS", "DIVENDRES", "DISSABTE",
            "DIUMENGE" };

    /** Horas que se da por "en juego" un partido sin resultado; pasado ese tiempo se da por terminado. */
    static final long HORAS_EN_JUEGO = 3;

    private final EquipoGestionDao equipoGestionDao;
    private final EquipoDao equipoDao;
    private final PartidoDao partidoDao;
    private final EquipoFfcvDao equipoFfcvDao;
    private final TemporadaDao temporadaDao;
    private final ImagenRedesDao imagenRedesDao;
    private final PartidoLiveEventoDao eventoDao;
    private final FondosCartelesService fondos;
    private final Clock clock;

    /** El nombre del equipo en EQUIPO ("Primer Equipo", "Filial"). */
    private final String nombreEquipoBd;

    /** Cómo se llama el equipo en los textos ("Mutxamel C.F.", "Mutxamel C.F. “B”"). */
    private final String nombreEnTextos;

    private final CartelRedes cartel;

    CartelPartidoServiceBase(EquipoGestionDao equipoGestionDao, EquipoDao equipoDao, PartidoDao partidoDao,
            EquipoFfcvDao equipoFfcvDao, TemporadaDao temporadaDao, ImagenRedesDao imagenRedesDao,
            PartidoLiveEventoDao eventoDao, FondosCartelesService fondos, Clock clock, String nombreEquipoBd,
            String nombreEnTextos, CartelRedes cartel) {

        this.equipoGestionDao = equipoGestionDao;
        this.equipoDao = equipoDao;
        this.partidoDao = partidoDao;
        this.equipoFfcvDao = equipoFfcvDao;
        this.temporadaDao = temporadaDao;
        this.imagenRedesDao = imagenRedesDao;
        this.eventoDao = eventoDao;
        this.fondos = fondos;
        this.clock = clock;
        this.nombreEquipoBd = nombreEquipoBd;
        this.nombreEnTextos = nombreEnTextos;
        this.cartel = cartel;
    }

    // ---- lo que comparten las rutas ------------------------------------------------------------------

    /** El próximo partido del equipo y sus datos, o {@code disponible = false} con el motivo. */
    PartidoPrimerEquipoResponse respuestaPartido(Long usuarioAppId) {

        validarAcceso(usuarioAppId);

        Eleccion eleccion = elegir();

        if (eleccion.partido == null) {
            return PartidoPrimerEquipoResponse.noDisponible(eleccion.motivo);
        }

        Partido p = eleccion.partido;
        LocalDate dia = aLocalDate(p.getDia());
        String tipo = tipoDe(p);
        String competicion = competicionTexto(tipo, competicionFfcv(p.getEquipoId(), dia));
        boolean rivalPropio = NombreClub.esPropio(p.getRival());
        String campo = CampoCartel.paraTexto(p.isLocal(), p.getCampo());

        return new PartidoPrimerEquipoResponse(true, null, p.getId(), p.isLocal(), p.getRival(),
                rivalPropio ? null : EscudosRivales.url(p.getClubRivalId(), p.getEscudoRivalVersion()), dia,
                p.getHora(), campo.isEmpty() ? null : campo, competicion, p.getJornada(), tipo,
                new TextoPublicacion(textoPublicacion(p, dia, campo, "es", nombreEnTextos),
                        textoPublicacion(p, dia, campo, "ca", nombreEnTextos)));
    }

    /**
     * Valida el acceso y los parámetros y elige el partido para el PNG.
     *
     * @throws CartelNoDisponibleException si no hay partido o ya ha empezado
     */
    Peticion prepararPng(Long usuarioAppId, String formato, String idioma, boolean racha) {

        validarAcceso(usuarioAppId);

        Formato formatoElegido = formato(formato);
        String idiomaElegido = idioma(idioma);

        Eleccion eleccion = elegir();

        if (eleccion.partido == null) {
            throw new CartelNoDisponibleException(eleccion.motivo);
        }

        return new Peticion(formatoElegido, idiomaElegido, comun(eleccion.partido, idiomaElegido, racha));
    }

    /** El formato, el idioma y lo común del partido ya resuelto. */
    record Peticion(Formato formato, String idioma, Comun comun) {
    }

    /**
     * Lo que dibujan los dos carteles del partido, ya en el idioma elegido.
     *
     * @param nombreRival    en mayúsculas
     * @param escudoRival    PNG del rival o null (si es un equipo nuestro no se pide: se pinta el del Mutxamel)
     * @param hora           "HH:mmH" o vacío si el partido no tiene hora
     * @param campoLineas    el campo para el cartel: en casa las tres líneas fijas, fuera el campo recortado en mayúsculas
     * @param racha          G, E o P del más antiguo al más reciente
     */
    record Comun(boolean local, String nombreRival, byte[] escudoRival, boolean rivalPropio, Competicion competicion,
            Integer jornada, String diaSemana, String fecha, String hora, String campoLineas, List<String> racha,
            boolean ca) {
    }

    /** El fondo del cartel: el de la tabla si existe y, si no, el de resources. */
    byte[] fondo() {
        return fondos.actual(cartel).bytes();
    }

    // ---- qué partido -------------------------------------------------------------------------------

    private record Eleccion(Partido partido, String motivo) {
    }

    /**
     * El próximo partido del equipo: se saltan los cancelados y los ya terminados (con resultado —goles o resultado
     * guardado—, con el directo en FINAL o que empezaron hace más de {@link #HORAS_EN_JUEGO} horas) y se elige el primero
     * que queda por fecha y hora. Si su hora de inicio aún no ha llegado se puede generar el cartel; si ya ha empezado
     * (en juego: menos de 3 horas y sin resultado), YA_EMPEZADO. Sin ninguno, SIN_PARTIDO.
     */
    private Eleccion elegir() {

        Equipo equipo = equipoDao.obtenerEquipoPorNombre(nombreEquipoBd);

        if (equipo == null) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.SIN_PARTIDO);
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDate ayer = ahora.toLocalDate().minusDays(1);

        Partido siguiente = partidoDao.obtenerPorEquipo(equipo.getId()).stream()
                .filter(p -> !p.isCancelado() && p.getDia() != null && !aLocalDate(p.getDia()).isBefore(ayer))
                .sorted(Comparator.comparing(CartelPartidoServiceBase::inicio)
                        .thenComparing(Partido::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .filter(p -> !terminado(p, ahora)).findFirst().orElse(null);

        if (siguiente == null) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.SIN_PARTIDO);
        }

        if (!inicio(siguiente).isAfter(ahora)) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.YA_EMPEZADO);
        }

        return new Eleccion(siguiente, null);
    }

    /**
     * Un partido terminado: tiene resultado (goles o resultado guardado), su directo llegó a FINAL o empezó hace más de
     * 3 horas. Un partido que aún no ha empezado nunca está terminado.
     */
    private boolean terminado(Partido p, LocalDateTime ahora) {

        LocalDateTime inicio = inicio(p);

        if (inicio.isAfter(ahora)) {
            return false;
        }

        if ((p.getGolesFavor() != null && p.getGolesContra() != null)
                || (p.getResultado() != null && !p.getResultado().isBlank())) {
            return true;
        }

        if (!inicio.plusHours(HORAS_EN_JUEGO).isAfter(ahora)) {
            return true;
        }

        return eventoDao.obtenerNoAnuladosPorPartido(p.getId()).stream()
                .anyMatch(e -> PartidoLiveEvento.FINAL.equals(e.getTipo()));
    }

    /** DIA + HORA válida, o el día a medianoche (mismo criterio que los recordatorios de partidos). */
    static LocalDateTime inicio(Partido p) {

        LocalDate dia = aLocalDate(p.getDia());
        LocalTime hora = hora(p.getHora());

        return hora == null ? dia.atStartOfDay() : dia.atTime(hora);
    }

    static LocalTime hora(String texto) {

        if (texto == null) {
            return null;
        }

        Matcher m = HORA.matcher(texto);

        if (!m.find()) {
            return null;
        }

        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));

        return h > 23 || min > 59 ? null : LocalTime.of(h, min);
    }

    // ---- datos comunes del cartel --------------------------------------------------------------------

    private Comun comun(Partido p, String idioma, boolean racha) {

        boolean ca = "ca".equals(idioma);
        LocalDate dia = aLocalDate(p.getDia());
        boolean rivalPropio = NombreClub.esPropio(p.getRival());

        byte[] escudo = null;

        if (!rivalPropio && p.getClubRivalId() != null) {
            escudo = imagenRedesDao.escudoRival(p.getClubRivalId());
        }

        LocalTime hora = hora(p.getHora());

        return new Comun(p.isLocal(), p.getRival() == null ? "" : p.getRival().trim().toUpperCase(Locale.ROOT),
                escudo, rivalPropio, competicion(tipoDe(p), competicionFfcv(p.getEquipoId(), dia), ca),
                p.getJornada(), nombreDia(dia, ca), dia.format(FECHA),
                hora == null ? "" : hora.format(HORA_TEXTO) + "H", CampoCartel.paraCartel(p.isLocal(), p.getCampo()),
                racha ? racha(p, dia) : List.of(), ca);
    }

    /** Los últimos 5 resultados antes del partido, del más antiguo al más reciente (G, E o P). */
    private List<String> racha(Partido p, LocalDate dia) {

        List<String> resultados = new ArrayList<>();

        for (Partido anterior : partidoDao.obtenerUltimosConResultado(p.getEquipoId(), dia, PARTIDOS_RACHA)) {

            int favor = anterior.getGolesFavor();
            int contra = anterior.getGolesContra();

            resultados.add(favor > contra ? "G" : favor == contra ? "E" : "P");
        }

        // La consulta devuelve del más reciente al más antiguo; en el cartel va al revés.
        Collections.reverse(resultados);

        return resultados;
    }

    // ---- competición ----------------------------------------------------------------------------------

    /** El logo de la competición o, si no lo hay, el texto que lo sustituye. */
    record Competicion(String logo, String texto) {
    }

    /**
     * COPA → La Nostra Copa; AMISTOSO y TORNEO → su texto; LIGA → el logo de Primera o Segona FFCV según la competición
     * del equipo y, si no encaja con ninguno, la competición escrita (nada si no se conoce).
     */
    static Competicion competicion(String tipo, String competicionFfcv, boolean ca) {

        switch (tipo) {
            case "COPA":
                return new Competicion(LOGO_COPA, null);
            case "AMISTOSO":
                return new Competicion(null, ca ? "PARTIT AMISTÓS" : "PARTIDO AMISTOSO");
            case "TORNEO":
                return new Competicion(null, ca ? "TORNEIG" : "TORNEO");
            default:
                break;
        }

        if (competicionFfcv == null || competicionFfcv.isBlank()) {
            return new Competicion(null, null);
        }

        String normalizada = sinAcentos(competicionFfcv).toUpperCase(Locale.ROOT);

        if (normalizada.contains("PRIMERA")) {
            return new Competicion(LOGO_PRIMERA, null);
        }

        if (normalizada.contains("SEGONA") || normalizada.contains("SEGUNDA")) {
            return new Competicion(LOGO_SEGONA, null);
        }

        return new Competicion(null, competicionFfcv.trim().toUpperCase(Locale.ROOT));
    }

    private static String competicionTexto(String tipo, String competicionFfcv) {

        return switch (tipo) {
            case "COPA" -> "La Nostra Copa";
            case "AMISTOSO" -> "Amistoso";
            case "TORNEO" -> "Torneo";
            default -> competicionFfcv;
        };
    }

    private String competicionFfcv(Long equipoId, LocalDate dia) {

        Temporada temporada = temporadaDelPartido(dia);

        if (temporada == null) {
            return null;
        }

        EquipoFfcv ffcv = equipoFfcvDao.obtenerPorEquipoYTemporada(equipoId, temporada.getId());

        return ffcv == null || ffcv.getCompeticion() == null || ffcv.getCompeticion().isBlank() ? null
                : ffcv.getCompeticion().trim();
    }

    /** La temporada cuyas fechas contienen el día del partido; si ninguna, la activa. */
    private Temporada temporadaDelPartido(LocalDate dia) {

        for (Temporada temporada : temporadaDao.obtenerTodos()) {

            if (temporada.getFechaInicio() != null && temporada.getFechaFin() != null
                    && !dia.isBefore(aLocalDate(temporada.getFechaInicio()))
                    && !dia.isAfter(aLocalDate(temporada.getFechaFin()))) {
                return temporada;
            }
        }

        return temporadaDao.obtenerTemporadaActiva();
    }

    // ---- texto para la publicación --------------------------------------------------------------------

    /** Con el nombre del club del Primer Equipo ("Mutxamel C.F."). */
    static String textoPublicacion(Partido p, LocalDate dia, String campo, String idioma) {
        return textoPublicacion(p, dia, campo, idioma, NOMBRE_MUTXAMEL);
    }

    /**
     * "Próximo partido: {local} – {visitante}. {Día} {dd/MM} a las {HH:mm}, {campo}. ¡Os esperamos! #hashtags" (en
     * catalán, "Pròxim partit: ... a les ... Us esperem!"). Sin hora o sin campo se omiten esas partes.
     */
    static String textoPublicacion(Partido p, LocalDate dia, String campo, String idioma, String nombreClub) {

        boolean ca = "ca".equals(idioma);
        String rival = p.getRival() == null ? "" : p.getRival().trim();
        String local = p.isLocal() ? nombreClub : rival;
        String visitante = p.isLocal() ? rival : nombreClub;
        LocalTime hora = hora(p.getHora());

        StringBuilder texto = new StringBuilder(ca ? "Pròxim partit: " : "Próximo partido: ");
        texto.append(local).append(" – ").append(visitante).append(visitante.endsWith(".") ? " " : ". ");
        texto.append(capitalizar(nombreDia(dia, ca))).append(' ').append(dia.format(DIA_MES));

        if (hora != null) {
            texto.append(ca ? " a les " : " a las ").append(hora.format(HORA_TEXTO));
        }

        if (campo != null && !campo.isBlank()) {
            texto.append(", ").append(campo);
        }

        texto.append(ca ? ". Us esperem! " : ". ¡Os esperamos! ").append(TextoPublicacionRedes.HASHTAGS);

        return texto.toString();
    }

    static String nombreDia(LocalDate dia, boolean ca) {

        DayOfWeek d = dia.getDayOfWeek();

        return (ca ? DIAS_CA : DIAS_ES)[d.getValue() - 1];
    }

    private static String capitalizar(String mayusculas) {
        return mayusculas.charAt(0) + mayusculas.substring(1).toLowerCase(Locale.ROOT);
    }

    // ---- utilidades --------------------------------------------------------------------------------------

    static String tipoDe(Partido p) {

        String tipo = p.getTipo() == null ? "" : p.getTipo().trim().toUpperCase(Locale.ROOT);

        return switch (tipo) {
            case "COPA", "AMISTOSO", "TORNEO" -> tipo;
            default -> "LIGA";
        };
    }

    static Formato formato(String formato) {

        String f = formato == null || formato.isBlank() ? "PUBLICACION" : formato.trim().toUpperCase(Locale.ROOT);

        try {
            return Formato.valueOf(f);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El formato debe ser PUBLICACION o HISTORIA");
        }
    }

    static String idioma(String idioma) {

        String i = idioma == null || idioma.isBlank() ? "es" : idioma.trim().toLowerCase(Locale.ROOT);

        if (!"es".equals(i) && !"ca".equals(i)) {
            throw new IllegalArgumentException("El idioma debe ser es o ca");
        }

        return i;
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static LocalDate aLocalDate(Date fecha) {

        if (fecha instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void validarAcceso(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0 || !equipoGestionDao.esRetransmisionOAdmin(usuarioAppId)) {
            throw new SecurityException(
                    "Solo Retransmisión y los administradores de la app pueden gestionar las imágenes para redes");
        }
    }
}
