package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.PartidoAlineacionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AlineacionJugador;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.AlineacionFicha;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.Cabecera;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.EventoFicha;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.FormaFicha;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.Suplente;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.Tarjeta;
import com.mikedev.mutxamelcf.model.FichaPartidoDTO.Titular;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.JugadorFicha;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.FichaPartidoService;
import com.mikedev.mutxamelcf.util.ImageUtils;

/**
 * Ficha pública del partido. Ligera: una consulta para el partido, una para los eventos, dos para la alineación, una
 * para los jugadores (con la miniatura solo de los mayores de edad), la temporada, la competición y los últimos
 * resultados; sin consultas por jugador ni por evento.
 */
@Service
public class FichaPartidoServiceImpl implements FichaPartidoService {

    private static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";
    private static final String NOMBRE_CLUB = "Mutxamel CF";
    private static final int PARTIDOS_FORMA = 5;
    private static final int MAYORIA_DE_EDAD = 18;

    private final PartidoDao partidoDao;
    private final EquipoDao equipoDao;
    private final PartidoLiveEventoDao eventoDao;
    private final PartidoAlineacionDao alineacionDao;
    private final TemporadaDao temporadaDao;
    private final EquipoFfcvDao equipoFfcvDao;
    private final TextosAvisos textos;
    private final Clock clock;

    public FichaPartidoServiceImpl(
            PartidoDao partidoDao,
            EquipoDao equipoDao,
            PartidoLiveEventoDao eventoDao,
            PartidoAlineacionDao alineacionDao,
            TemporadaDao temporadaDao,
            EquipoFfcvDao equipoFfcvDao,
            TextosAvisos textos,
            Clock clock) {

        this.partidoDao = partidoDao;
        this.equipoDao = equipoDao;
        this.eventoDao = eventoDao;
        this.alineacionDao = alineacionDao;
        this.temporadaDao = temporadaDao;
        this.equipoFfcvDao = equipoFfcvDao;
        this.textos = textos;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPartidoDTO obtenerFicha(Long partidoId, String idioma) {

        Partido partido = partidoId == null ? null : partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            return null;
        }

        Equipo primerEquipo = equipoDao.obtenerEquipoPorNombre(EQUIPO_PRIMER_EQUIPO);

        if (primerEquipo == null || !primerEquipo.getId().equals(partido.getEquipoId())) {
            return null;
        }

        LocalDate dia = partido.getDia() == null ? null : aLocalDate(partido.getDia());

        List<PartidoLiveEvento> eventos = eventoDao.obtenerNoAnuladosPorPartido(partidoId);
        List<AlineacionJugador> alineacion = alineacionDao.obtener(partidoId);

        Map<Long, JugadorFicha> jugadores = jugadores(eventos, alineacion, dia, primerEquipo.getId());

        return new FichaPartidoDTO(
                cabecera(partido, dia, eventos, idioma),
                eventos(eventos, jugadores, idioma),
                alineacion(partidoId, alineacion, eventos, jugadores),
                forma(partido, dia, primerEquipo.getId()));
    }

    // ------------------------------------------------------------------
    // Cabecera
    // ------------------------------------------------------------------

    private Cabecera cabecera(Partido partido, LocalDate dia, List<PartidoLiveEvento> eventos, String idioma) {

        String rival = partido.getRival() == null ? "" : partido.getRival();

        String fase = fase(eventos);

        Integer golesFavor = partido.getGolesFavor();
        Integer golesContra = partido.getGolesContra();

        if (fase != null) {
            golesFavor = contar(eventos, PartidoLiveEvento.GOL_FAVOR);
            golesContra = contar(eventos, PartidoLiveEvento.GOL_CONTRA);
        }

        String estado;

        if (partido.isCancelado()) {
            estado = FichaPartidoDTO.CANCELADO;
        } else if (fase != null) {
            estado = switch (fase) {
                case "DESCANSO" -> FichaPartidoDTO.DESCANSO;
                case "FINAL" -> FichaPartidoDTO.FINALIZADO;
                default -> FichaPartidoDTO.EN_JUEGO;
            };
        } else {
            // Sin retransmisión: el resultado guardado (a mano o importado) dice si ya se jugó.
            estado = partido.getGolesFavor() != null && partido.getGolesContra() != null
                    ? FichaPartidoDTO.FINALIZADO
                    : FichaPartidoDTO.PROXIMO;
        }

        return new Cabecera(
                partido.getId(),
                partido.isLocal() ? NOMBRE_CLUB : rival,
                partido.isLocal() ? rival : NOMBRE_CLUB,
                dia, partido.getHora(), golesFavor, golesContra, estado, competicion(partido, dia, idioma));
    }

    /** La fase que marcan los eventos ("PRIMERA_PARTE", "DESCANSO", "SEGUNDA_PARTE", "FINAL"); null sin retransmisión. */
    private static String fase(List<PartidoLiveEvento> eventos) {

        String fase = null;

        for (PartidoLiveEvento evento : eventos) {
            switch (evento.getTipo()) {
                case PartidoLiveEvento.INICIO -> fase = "PRIMERA_PARTE";
                case PartidoLiveEvento.DESCANSO -> fase = "DESCANSO";
                case PartidoLiveEvento.SEGUNDA_PARTE -> fase = "SEGUNDA_PARTE";
                case PartidoLiveEvento.FINAL -> fase = "FINAL";
                default -> {
                    // goles, cambios y tarjetas no cambian la fase
                }
            }
        }

        return fase;
    }

    /*
     * Liga: la competición de EQUIPOS_FFCV del equipo en la temporada del partido y, si la tiene, " · Jornada N".
     * Copa: "Copa" y, si la hay, " · competición". Amistoso: solo "Amistoso".
     */
    private String competicion(Partido partido, LocalDate dia, String idioma) {

        String tipo = partido.getTipo() == null ? "" : partido.getTipo().trim().toUpperCase();

        if ("AMISTOSO".equals(tipo)) {
            return textos.texto(idioma, "ficha.amistoso");
        }

        String competicion = competicionFfcv(partido.getEquipoId(), dia);

        if ("COPA".equals(tipo)) {

            String copa = textos.texto(idioma, "ficha.copa");

            return competicion == null ? copa : copa + " · " + competicion;
        }

        String jornada = partido.getJornada() == null ? null
                : textos.texto(idioma, "ficha.jornada", "n", String.valueOf(partido.getJornada()));

        if (competicion == null) {
            return jornada == null ? textos.texto(idioma, "ficha.liga") : jornada;
        }

        return jornada == null ? competicion : competicion + " · " + jornada;
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

        if (dia != null) {

            for (Temporada temporada : temporadaDao.obtenerTodos()) {

                if (temporada.getFechaInicio() != null && temporada.getFechaFin() != null
                        && !dia.isBefore(aLocalDate(temporada.getFechaInicio()))
                        && !dia.isAfter(aLocalDate(temporada.getFechaFin()))) {
                    return temporada;
                }
            }
        }

        return temporadaDao.obtenerTemporadaActiva();
    }

    // ------------------------------------------------------------------
    // Jugadores
    // ------------------------------------------------------------------

    /*
     * Todos los jugadores que salen en la ficha, en una consulta. Con alineación se piden con la foto (solo se lee
     * la de los mayores de edad); sin ella, solo los nombres de los eventos y sin tocar ninguna foto.
     */
    private Map<Long, JugadorFicha> jugadores(
            List<PartidoLiveEvento> eventos, List<AlineacionJugador> alineacion, LocalDate dia, Long primerEquipoId) {

        Set<Long> ids = new HashSet<>();

        alineacion.forEach(a -> ids.add(a.jugadorId()));

        for (PartidoLiveEvento evento : eventos) {

            if (evento.getJugadorId() != null) {
                ids.add(evento.getJugadorId());
            }

            if (evento.getJugadorEntraId() != null) {
                ids.add(evento.getJugadorEntraId());
            }
        }

        Map<Long, JugadorFicha> jugadores = new HashMap<>();

        if (ids.isEmpty()) {
            return jugadores;
        }

        if (alineacion.isEmpty()) {

            for (JugadorBasico jugador : eventoDao.obtenerJugadoresBasicos(ids)) {
                jugadores.put(jugador.id(), new JugadorFicha(jugador, null));
            }

            return jugadores;
        }

        int anioPartido = (dia == null ? LocalDate.now(clock) : dia).getYear();

        for (JugadorFicha jugador : alineacionDao.obtenerJugadoresFicha(ids, anioPartido - MAYORIA_DE_EDAD,
                primerEquipoId)) {
            jugadores.put(jugador.jugador().id(), jugador);
        }

        return jugadores;
    }

    private static String nombre(Map<Long, JugadorFicha> jugadores, Long id) {

        JugadorFicha jugador = id == null ? null : jugadores.get(id);

        return jugador == null ? null : jugador.jugador().nombreEnDirecto();
    }

    // ------------------------------------------------------------------
    // Eventos
    // ------------------------------------------------------------------

    private List<EventoFicha> eventos(List<PartidoLiveEvento> eventos, Map<Long, JugadorFicha> jugadores,
            String idioma) {

        Set<Long> dobles = TarjetasEventos.dobles(eventos);

        List<EventoFicha> resultado = new ArrayList<>();

        int favor = 0;
        int contra = 0;

        for (PartidoLiveEvento evento : eventos) {

            switch (evento.getTipo()) {
                case PartidoLiveEvento.GOL_FAVOR -> {
                    favor++;
                    resultado.add(new EventoFicha(evento.getTipo(), evento.getMinuto(),
                            autorGol(evento, jugadores, idioma), null, favor, contra, null, false));
                }
                case PartidoLiveEvento.GOL_CONTRA -> {
                    contra++;
                    resultado.add(new EventoFicha(evento.getTipo(), evento.getMinuto(), null, null, favor, contra,
                            null, false));
                }
                case PartidoLiveEvento.CAMBIO -> resultado.add(new EventoFicha(evento.getTipo(), evento.getMinuto(),
                        nombre(jugadores, evento.getJugadorId()), nombre(jugadores, evento.getJugadorEntraId()),
                        null, null, null, false));
                case PartidoLiveEvento.TARJETA_AMARILLA, PartidoLiveEvento.TARJETA_ROJA -> {
                    boolean doble = dobles.contains(evento.getId());
                    // La doble amarilla es una expulsión: en la ficha sale como roja.
                    String tipo = doble ? PartidoLiveEvento.TARJETA_ROJA : evento.getTipo();
                    resultado.add(new EventoFicha(tipo, evento.getMinuto(), nombre(jugadores, evento.getJugadorId()),
                            null, null, null, null, doble));
                }
                case PartidoLiveEvento.DESCANSO -> resultado.add(new EventoFicha(evento.getTipo(), null, null, null,
                        favor, contra, textos.texto(idioma, "ficha.separador.descanso"), false));
                case PartidoLiveEvento.FINAL -> resultado.add(new EventoFicha(evento.getTipo(), null, null, null,
                        favor, contra, textos.texto(idioma, "ficha.separador.final"), false));
                default -> {
                    // INICIO y SEGUNDA_PARTE no salen en la ficha
                }
            }
        }

        // Lo último primero.
        Collections.reverse(resultado);

        return resultado;
    }

    private String autorGol(PartidoLiveEvento evento, Map<Long, JugadorFicha> jugadores, String idioma) {

        if (evento.getJugadorId() != null) {
            return nombre(jugadores, evento.getJugadorId());
        }

        String texto = evento.getAutorTexto();

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return PartidoEnVivoServiceImpl.PROPIA_PUERTA.equals(texto)
                ? textos.texto(idioma, "directo.golPropiaPuerta")
                : texto.trim();
    }

    // ------------------------------------------------------------------
    // Alineación
    // ------------------------------------------------------------------

    private AlineacionFicha alineacion(Long partidoId, List<AlineacionJugador> filas,
            List<PartidoLiveEvento> eventos, Map<Long, JugadorFicha> jugadores) {

        if (filas.isEmpty()) {
            return new AlineacionFicha(null, List.of(), List.of());
        }

        String formacion = alineacionDao.obtenerFormacion(partidoId);

        Map<Long, Integer> salidas = new HashMap<>();
        Map<Long, Integer> entradas = new HashMap<>();
        Map<Long, List<Tarjeta>> tarjetas = new HashMap<>();

        Set<Long> dobles = TarjetasEventos.dobles(eventos);

        for (PartidoLiveEvento evento : eventos) {

            if (PartidoLiveEvento.CAMBIO.equals(evento.getTipo())) {
                salidas.put(evento.getJugadorId(), evento.getMinuto());
                entradas.put(evento.getJugadorEntraId(), evento.getMinuto());
            } else if (TarjetasEventos.esTarjeta(evento.getTipo())) {

                boolean doble = dobles.contains(evento.getId());
                String color = doble || PartidoLiveEvento.TARJETA_ROJA.equals(evento.getTipo()) ? "ROJA" : "AMARILLA";

                tarjetas.computeIfAbsent(evento.getJugadorId(), id -> new ArrayList<>())
                        .add(new Tarjeta(color, evento.getMinuto(), doble));
            }
        }

        int numeroTitulares = (int) filas.stream().filter(AlineacionJugador::titular).count();

        // Con una formación que no cuadra con los titulares (no debería pasar) no se inventan posiciones.
        boolean conPosiciones = formacion != null && formacionValida(formacion, numeroTitulares);

        List<Titular> titulares = new ArrayList<>();
        List<Suplente> suplentes = new ArrayList<>();

        for (AlineacionJugador fila : filas) {

            JugadorFicha ficha = jugadores.get(fila.jugadorId());

            if (ficha == null) {
                continue;
            }

            JugadorBasico jugador = ficha.jugador();
            String foto = ImageUtils.convertirAMiniaturaBase64(ficha.foto());
            List<Tarjeta> susTarjetas = tarjetas.getOrDefault(fila.jugadorId(), List.of());

            if (fila.titular()) {

                int[] posicion = conPosiciones ? Formaciones.posicion(formacion, fila.orden()) : null;

                titulares.add(new Titular(jugador.dorsal(), jugador.nombreEnDirecto(),
                        posicion == null ? null : posicion[0], posicion == null ? null : posicion[1],
                        salidas.containsKey(fila.jugadorId()), salidas.get(fila.jugadorId()), susTarjetas, foto));

            } else {

                suplentes.add(new Suplente(jugador.dorsal(), jugador.nombreEnDirecto(),
                        entradas.containsKey(fila.jugadorId()), entradas.get(fila.jugadorId()), susTarjetas, foto));
            }

        }

        return new AlineacionFicha(formacion, titulares, suplentes);
    }

    private static boolean formacionValida(String formacion, int titulares) {

        try {
            return Formaciones.titulares(formacion) == titulares;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Forma
    // ------------------------------------------------------------------

    private List<FormaFicha> forma(Partido partido, LocalDate dia, Long primerEquipoId) {

        if (dia == null) {
            return List.of();
        }

        List<FormaFicha> forma = new ArrayList<>();

        for (Partido anterior : partidoDao.obtenerUltimosConResultado(primerEquipoId, dia, PARTIDOS_FORMA)) {

            int favor = anterior.getGolesFavor();
            int contra = anterior.getGolesContra();

            forma.add(new FormaFicha(anterior.getId(), aLocalDate(anterior.getDia()), anterior.isLocal(),
                    anterior.getRival(), favor, contra, favor > contra ? "G" : favor == contra ? "E" : "P"));
        }

        return forma;
    }

    private static int contar(List<PartidoLiveEvento> eventos, String tipo) {
        return (int) eventos.stream().filter(e -> tipo.equals(e.getTipo())).count();
    }

    private static LocalDate aLocalDate(Date fecha) {

        if (fecha instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
