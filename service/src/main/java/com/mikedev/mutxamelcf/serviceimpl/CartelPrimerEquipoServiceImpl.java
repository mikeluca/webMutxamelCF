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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.mikedev.mutxamelcf.service.CartelPrimerEquipoService;
import com.mikedev.mutxamelcf.service.FondosCartelesService;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Datos;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;

@Service
public class CartelPrimerEquipoServiceImpl implements CartelPrimerEquipoService {

    static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";
    static final String NOMBRE_MUTXAMEL = "Mutxamel C.F.";

    private static final int PARTIDOS_RACHA = 5;
    private static final Pattern HORA = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    static final String LOGO_PRIMERA = "/redes/logos/competiciones/primera-ffcv.png";
    static final String LOGO_SEGONA = "/redes/logos/competiciones/segona-ffcv.png";
    static final String LOGO_COPA = "/redes/logos/competiciones/la-nostra-copa.png";

    private static final String[] DIAS_ES = { "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO",
            "DOMINGO" };
    private static final String[] DIAS_CA = { "DILLUNS", "DIMARTS", "DIMECRES", "DIJOUS", "DIVENDRES", "DISSABTE",
            "DIUMENGE" };

    private final EquipoGestionDao equipoGestionDao;
    private final EquipoDao equipoDao;
    private final PartidoDao partidoDao;
    private final EquipoFfcvDao equipoFfcvDao;
    private final TemporadaDao temporadaDao;
    private final ImagenRedesDao imagenRedesDao;
    private final PartidoLiveEventoDao eventoDao;
    private final FondosCartelesService fondos;
    private final CartelPrimerEquipoRenderer renderer;
    private final Clock clock;

    public CartelPrimerEquipoServiceImpl(EquipoGestionDao equipoGestionDao, EquipoDao equipoDao, PartidoDao partidoDao,
            EquipoFfcvDao equipoFfcvDao, TemporadaDao temporadaDao, ImagenRedesDao imagenRedesDao,
            PartidoLiveEventoDao eventoDao,
            FondosCartelesService fondos, CartelPrimerEquipoRenderer renderer, Clock clock) {

        this.equipoGestionDao = equipoGestionDao;
        this.equipoDao = equipoDao;
        this.partidoDao = partidoDao;
        this.equipoFfcvDao = equipoFfcvDao;
        this.temporadaDao = temporadaDao;
        this.imagenRedesDao = imagenRedesDao;
        this.eventoDao = eventoDao;
        this.fondos = fondos;
        this.renderer = renderer;
        this.clock = clock;
    }

    // ---- API ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PartidoPrimerEquipoResponse partido(Long usuarioAppId) {

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
                new TextoPublicacion(textoPublicacion(p, dia, campo, "es"), textoPublicacion(p, dia, campo, "ca")));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] png(Long usuarioAppId, String formato, String idioma, boolean racha, boolean qr, boolean hashtags) {

        validarAcceso(usuarioAppId);

        Formato formatoElegido = formato(formato);
        String idiomaElegido = idioma(idioma);

        Eleccion eleccion = elegir();

        if (eleccion.partido == null) {
            throw new CartelNoDisponibleException(eleccion.motivo);
        }

        Partido p = eleccion.partido;
        LocalDate dia = aLocalDate(p.getDia());

        Datos datos = datos(p, dia, idiomaElegido, racha, qr, hashtags);
        byte[] fondo = fondos.actual(CartelRedes.PRIMER_EQUIPO).bytes();

        return renderer.renderizar(formatoElegido, datos, fondo);
    }

    // ---- qué partido -------------------------------------------------------------------------------

    private record Eleccion(Partido partido, String motivo) {
    }

    /**
     * El próximo partido del Primer Equipo: se saltan los cancelados y los ya terminados (con resultado —goles o
     * resultado guardado—, con el directo en FINAL o que empezaron hace más de {@link #HORAS_EN_JUEGO} horas) y se
     * elige el primero que queda por fecha y hora. Si su hora de inicio aún no ha llegado se puede generar el cartel;
     * si ya ha empezado (en juego: menos de 3 horas y sin resultado), YA_EMPEZADO. Sin ninguno, SIN_PARTIDO.
     */
    private Eleccion elegir() {

        Equipo primerEquipo = equipoDao.obtenerEquipoPorNombre(EQUIPO_PRIMER_EQUIPO);

        if (primerEquipo == null) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.SIN_PARTIDO);
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDate ayer = ahora.toLocalDate().minusDays(1);

        Partido siguiente = partidoDao.obtenerPorEquipo(primerEquipo.getId()).stream()
                .filter(p -> !p.isCancelado() && p.getDia() != null && !aLocalDate(p.getDia()).isBefore(ayer))
                .sorted(Comparator.comparing(CartelPrimerEquipoServiceImpl::inicio)
                        .thenComparing(Partido::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .filter(p -> !terminado(p, ahora))
                .findFirst().orElse(null);

        if (siguiente == null) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.SIN_PARTIDO);
        }

        if (!inicio(siguiente).isAfter(ahora)) {
            return new Eleccion(null, PartidoPrimerEquipoResponse.YA_EMPEZADO);
        }

        return new Eleccion(siguiente, null);
    }

    /** Horas que se da por "en juego" un partido sin resultado; pasado ese tiempo se da por terminado. */
    static final long HORAS_EN_JUEGO = 3;

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

    // ---- datos del cartel ----------------------------------------------------------------------------

    private Datos datos(Partido p, LocalDate dia, String idioma, boolean racha, boolean qr, boolean hashtags) {

        boolean ca = "ca".equals(idioma);
        String tipo = tipoDe(p);
        boolean rivalPropio = NombreClub.esPropio(p.getRival());

        byte[] escudo = null;

        if (!rivalPropio && p.getClubRivalId() != null) {
            escudo = imagenRedesDao.escudoRival(p.getClubRivalId());
        }

        Competicion competicion = competicion(tipo, competicionFfcv(p.getEquipoId(), dia), ca);
        LocalTime hora = hora(p.getHora());

        List<String> resultados = racha ? racha(p, dia) : List.of();

        return new Datos(p.isLocal(), NOMBRE_MUTXAMEL.toUpperCase(Locale.ROOT),
                p.getRival() == null ? "" : p.getRival().trim().toUpperCase(Locale.ROOT), escudo, rivalPropio,
                ca ? "PRÒXIM PARTIT" : "PRÓXIMO PARTIDO", p.getJornada() == null ? null : "JORNADA " + p.getJornada(),
                competicion.logo, competicion.texto, nombreDia(dia, ca), dia.format(FECHA),
                hora == null ? "" : hora.format(DateTimeFormatter.ofPattern("HH:mm")) + "H",
                CampoCartel.paraCartel(p.isLocal(), p.getCampo()), ca ? "ÚLTIMS 5" : "ÚLTIMOS 5", resultados, hashtags,
                qr, ca ? "Descarrega l'app" : "Descarga la app");
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
     * del Primer Equipo y, si no encaja con ninguno, la competición escrita (nada si no se conoce).
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

    /**
     * "Próximo partido: {local} – {visitante}. {Día} {dd/MM} a las {HH:mm}, {campo}. ¡Os esperamos! #hashtags" (en
     * catalán, "Pròxim partit: ... a les ... Us esperem!"). Sin hora o sin campo se omiten esas partes.
     */
    static String textoPublicacion(Partido p, LocalDate dia, String campo, String idioma) {

        boolean ca = "ca".equals(idioma);
        String rival = p.getRival() == null ? "" : p.getRival().trim();
        String local = p.isLocal() ? NOMBRE_MUTXAMEL : rival;
        String visitante = p.isLocal() ? rival : NOMBRE_MUTXAMEL;
        LocalTime hora = hora(p.getHora());

        StringBuilder texto = new StringBuilder(ca ? "Pròxim partit: " : "Próximo partido: ");
        texto.append(local).append(" – ").append(visitante).append(visitante.endsWith(".") ? " " : ". ");
        texto.append(capitalizar(nombreDia(dia, ca))).append(' ').append(dia.format(DIA_MES));

        if (hora != null) {
            texto.append(ca ? " a les " : " a las ").append(hora.format(DateTimeFormatter.ofPattern("HH:mm")));
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

    private static String tipoDe(Partido p) {

        String tipo = p.getTipo() == null ? "" : p.getTipo().trim().toUpperCase(Locale.ROOT);

        return switch (tipo) {
            case "COPA", "AMISTOSO", "TORNEO" -> tipo;
            default -> "LIGA";
        };
    }

    private static Formato formato(String formato) {

        String f = formato == null || formato.isBlank() ? "PUBLICACION" : formato.trim().toUpperCase(Locale.ROOT);

        try {
            return Formato.valueOf(f);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El formato debe ser PUBLICACION o HISTORIA");
        }
    }

    private static String idioma(String idioma) {

        String i = idioma == null || idioma.isBlank() ? "es" : idioma.trim().toLowerCase(Locale.ROOT);

        if (!"es".equals(i) && !"ca".equals(i)) {
            throw new IllegalArgumentException("El idioma debe ser es o ca");
        }

        return i;
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static LocalDate aLocalDate(Date fecha) {

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
