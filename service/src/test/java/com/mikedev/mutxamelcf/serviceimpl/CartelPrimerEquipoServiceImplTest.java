package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesFondoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.FondoCartel;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CartelNoDisponibleException;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Datos;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;

/** El cartel "Próximo partido": qué partido, sus datos y el PNG que sale (con el renderizador real). */
class CartelPrimerEquipoServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // Jueves 19/11/2026, 12:00 en Madrid
    private static final Instant AHORA = Instant.parse("2026-11-19T11:00:00Z");
    private static final LocalDate SABADO = LocalDate.of(2026, 11, 21);

    private final EquipoGestionDao gestion = mock(EquipoGestionDao.class);
    private final EquipoDao equipoDao = mock(EquipoDao.class);
    private final PartidoDao partidoDao = mock(PartidoDao.class);
    private final EquipoFfcvDao ffcvDao = mock(EquipoFfcvDao.class);
    private final TemporadaDao temporadaDao = mock(TemporadaDao.class);
    private final ImagenRedesDao imagenRedesDao = mock(ImagenRedesDao.class);
    private final ImagenRedesFondoDao fondoDao = mock(ImagenRedesFondoDao.class);
    private final PartidoLiveEventoDao eventoDao = mock(PartidoLiveEventoDao.class);
    private final CartelPrimerEquipoRenderer renderer = spy(new CartelPrimerEquipoRenderer());

    private CartelPrimerEquipoServiceImpl servicio;
    private EquipoFfcv ffcv;

    @BeforeEach
    void setUp() {

        servicio = new CartelPrimerEquipoServiceImpl(gestion, equipoDao, partidoDao, ffcvDao, temporadaDao,
                imagenRedesDao, eventoDao, new FondosCartelesServiceImpl(fondoDao), renderer,
                Clock.fixed(AHORA, MADRID));

        // 7 es Retransmisión, 9 administrador de la app, 5 (un coordinador) no tiene acceso.
        when(gestion.esRetransmisionOAdmin(7L)).thenReturn(true);
        when(gestion.esRetransmisionOAdmin(9L)).thenReturn(true);

        Equipo primerEquipo = new Equipo();
        primerEquipo.setId(1L);
        when(equipoDao.obtenerEquipoPorNombre("Primer Equipo")).thenReturn(primerEquipo);

        Temporada temporada = new Temporada();
        temporada.setId(3L);
        temporada.setFechaInicio(Date.valueOf("2026-07-01"));
        temporada.setFechaFin(Date.valueOf("2027-06-30"));
        when(temporadaDao.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada);

        ffcv = new EquipoFfcv();
        ffcv.setCompeticion("Primera FFCV, Grup 4");
        when(ffcvDao.obtenerPorEquipoYTemporada(1L, 3L)).thenReturn(ffcv);

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(30L, SABADO, "17:30")));
    }

    private static Partido partido(long id, LocalDate dia, String hora) {

        Partido p = new Partido();
        p.setId(id);
        p.setEquipoId(1L);
        p.setRival("C.D. Salesianos Alicante");
        p.setDia(Date.valueOf(dia));
        p.setHora(hora);
        p.setCampo("Campo Mpal. Les Costeres F-11 Gata");
        p.setTipo("LIGA");
        p.setJornada(11);
        p.setLocal(true);
        p.setClubRivalId(40L);
        p.setEscudoRivalVersion(2L);

        return p;
    }

    private Partido unico() {
        return partidoDao.obtenerPorEquipo(1L).get(0);
    }

    private static Partido resultado(long id, int favor, int contra) {

        Partido p = partido(id, SABADO.minusDays(id), "17:00");
        p.setGolesFavor(favor);
        p.setGolesContra(contra);

        return p;
    }

    private static byte[] png(int ancho, int alto, Color color) throws Exception {

        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);

        return salida.toByteArray();
    }

    private static BufferedImage leer(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** Todo el lienzo en negro para que se vea solo lo que pinta el diseño. */
    private void fondoNegro() throws Exception {
        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(new FondoCartel("PRIMER_EQUIPO",
                png(1080, 1350, Color.BLACK), "image/png", 1080, 1350, LocalDateTime.now(), null));
    }

    private Datos datosDelUltimoRender() {

        ArgumentCaptor<Datos> datos = ArgumentCaptor.forClass(Datos.class);
        verify(renderer).renderizar(any(), datos.capture(), any());

        return datos.getValue();
    }

    // ---- permisos ----------------------------------------------------------------------------------------

    @Test
    void retransmisionYAdminSiPeroCoordinacionNo() {

        for (long usuario : new long[] { 7L, 9L }) {
            assertThat(servicio.partido(usuario).disponible()).isTrue();
            assertThat(servicio.png(usuario, "PUBLICACION", "es", true, true, true)).isNotEmpty();
        }

        assertThatThrownBy(() -> servicio.partido(5L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.png(5L, "PUBLICACION", "es", true, true, true))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio.partido(null)).isInstanceOf(SecurityException.class);
    }

    // ---- qué partido ---------------------------------------------------------------------------------------

    @Test
    void sinPartidosEsSinPartido() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of());

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo("SIN_PARTIDO");
        assertThat(r.partidoId()).isNull();

        assertThatThrownBy(() -> servicio.png(7L, "PUBLICACION", "es", true, true, true))
                .isInstanceOfSatisfying(CartelNoDisponibleException.class,
                        e -> assertThat(e.motivo()).isEqualTo("SIN_PARTIDO"));
    }

    @Test
    void sinEquipoPrimerEquipoEsSinPartido() {

        when(equipoDao.obtenerEquipoPorNombre("Primer Equipo")).thenReturn(null);

        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");
    }

    @Test
    void soloPartidosPasadosEsSinPartido() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 15), "17:30"),
                partido(2L, LocalDate.of(2026, 11, 18), "20:00")));

        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");
    }

    @Test
    void desdeLaHoraDeInicioEsYaEmpezadoAunqueNoHayaDirecto() {

        // Hoy (jueves 19) a las 11:59: ya ha empezado hace una hora.
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "11:59")));

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo("YA_EMPEZADO");

        assertThatThrownBy(() -> servicio.png(7L, "HISTORIA", "ca", true, true, true))
                .isInstanceOfSatisfying(CartelNoDisponibleException.class,
                        e -> assertThat(e.motivo()).isEqualTo("YA_EMPEZADO"));
    }

    @Test
    void yaEmpezadoNoSeSaltaAlPartidoDeLaSemanaSiguiente() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "10:00"),
                partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        assertThat(servicio.partido(7L).motivo()).isEqualTo("YA_EMPEZADO");
    }

    @Test
    void justoALaHoraDeInicioYaHaEmpezadoYUnMinutoAntesTodaviaNo() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "12:00")));
        assertThat(servicio.partido(7L).motivo()).isEqualTo("YA_EMPEZADO");

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "12:01")));
        assertThat(servicio.partido(7L).disponible()).isTrue();
    }

    @Test
    void unPartidoCanceladoSeSaltaYSeEligeElSiguiente() {

        Partido cancelado = partido(1L, LocalDate.of(2026, 11, 20), "17:30");
        cancelado.setCancelado(true);

        when(partidoDao.obtenerPorEquipo(1L))
                .thenReturn(List.of(cancelado, partido(2L, LocalDate.of(2026, 11, 28), "17:30")));

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isTrue();
        assertThat(r.partidoId()).isEqualTo(2L);
    }

    @Test
    void siElUnicoPartidoEstaCanceladoEsSinPartido() {

        Partido cancelado = partido(1L, LocalDate.of(2026, 11, 20), "17:30");
        cancelado.setCancelado(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(cancelado));

        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");
    }

    @Test
    void entreVariosFuturosSeEligeElMasProximoAunqueVenganDesordenados() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 12, 5), "17:30"),
                partido(2L, LocalDate.of(2026, 11, 22), "12:00"), partido(3L, LocalDate.of(2026, 11, 22), "10:00")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(3L);
    }

    // ---- partidos ya terminados ----------------------------------------------------------------------------

    @Test
    void unPartidoDeHoyTerminadoConResultadoSeSaltaYSeEligeElSiguiente() {

        Partido terminado = partido(1L, LocalDate.of(2026, 11, 19), "10:00");
        terminado.setGolesFavor(2);
        terminado.setGolesContra(1);

        when(partidoDao.obtenerPorEquipo(1L))
                .thenReturn(List.of(terminado, partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isTrue();
        assertThat(r.partidoId()).isEqualTo(2L);
    }

    @Test
    void elResultadoGuardadoComoTextoTambienCuentaComoTerminado() {

        Partido terminado = partido(1L, LocalDate.of(2026, 11, 19), "11:30");
        terminado.setResultado("2-1");

        when(partidoDao.obtenerPorEquipo(1L))
                .thenReturn(List.of(terminado, partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void unDirectoEnFinalCuentaComoTerminadoAunSinResultadoEnElPartido() {

        Partido enFinal = partido(1L, LocalDate.of(2026, 11, 19), "11:00");
        PartidoLiveEvento fin = new PartidoLiveEvento();
        fin.setTipo(PartidoLiveEvento.FINAL);

        when(eventoDao.obtenerNoAnuladosPorPartido(1L)).thenReturn(List.of(fin));
        when(partidoDao.obtenerPorEquipo(1L))
                .thenReturn(List.of(enFinal, partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void unPartidoEmpezadoHaceUnaHoraSinResultadoEsYaEmpezado() {

        // Ahora son las 12:00; empezó a las 11:00.
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "11:00"),
                partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo("YA_EMPEZADO");
    }

    @Test
    void empezadoHaceCuatroHorasSinResultadoSeDaPorTerminadoYSeEligeElSiguiente() {

        // Ahora son las 12:00; empezó a las 08:00.
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "08:00"),
                partido(2L, LocalDate.of(2026, 11, 26), "17:30")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void justoATresHorasYaSeDaPorTerminadoYUnMinutoAntesSigueEnJuego() {

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "09:00")));
        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");

        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "09:01")));
        assertThat(servicio.partido(7L).motivo()).isEqualTo("YA_EMPEZADO");
    }

    @Test
    void sinMasPartidosTrasUnoTerminadoEsSinPartido() {

        Partido terminado = partido(1L, LocalDate.of(2026, 11, 19), "10:00");
        terminado.setGolesFavor(0);
        terminado.setGolesContra(0);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(terminado));

        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");
        assertThatThrownBy(() -> servicio.png(7L, "PUBLICACION", "es", true, true, true))
                .isInstanceOfSatisfying(CartelNoDisponibleException.class,
                        e -> assertThat(e.motivo()).isEqualTo("SIN_PARTIDO"));
    }

    @Test
    void elDomingoPorLaNocheYaSePuedeHacerElCartelDelSiguientePartido() {

        // Domingo 22/11 a las 22:30: el partido de la tarde (17:30) ya terminó con resultado.
        Clock noche = Clock.fixed(Instant.parse("2026-11-22T21:30:00Z"), MADRID);
        CartelPrimerEquipoServiceImpl de = new CartelPrimerEquipoServiceImpl(gestion, equipoDao, partidoDao,
                ffcvDao, temporadaDao, imagenRedesDao, eventoDao, new FondosCartelesServiceImpl(fondoDao), renderer,
                noche);

        Partido tarde = partido(1L, LocalDate.of(2026, 11, 22), "17:30");
        tarde.setGolesFavor(3);
        tarde.setGolesContra(2);
        when(partidoDao.obtenerPorEquipo(1L))
                .thenReturn(List.of(tarde, partido(2L, LocalDate.of(2026, 11, 29), "17:30")));

        assertThat(de.partido(7L).partidoId()).isEqualTo(2L);
    }

    // ---- la respuesta JSON -------------------------------------------------------------------------------------

    @Test
    void laRespuestaTraeTodosLosDatosDelPartido() {

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isTrue();
        assertThat(r.motivo()).isNull();
        assertThat(r.partidoId()).isEqualTo(30L);
        assertThat(r.local()).isTrue();
        assertThat(r.rival()).isEqualTo("C.D. Salesianos Alicante");
        assertThat(r.escudoRivalUrl()).isEqualTo("/api/public/escudos-rivales/40?v=2");
        assertThat(r.dia()).isEqualTo(SABADO);
        assertThat(r.hora()).isEqualTo("17:30");
        assertThat(r.campo()).isEqualTo("Camp Nou Los Olmos (Mutxamel)");
        assertThat(r.competicion()).isEqualTo("Primera FFCV, Grup 4");
        assertThat(r.jornada()).isEqualTo(11);
        assertThat(r.tipo()).isEqualTo("LIGA");
    }

    @Test
    void fueraElCampoVaRecortadoYSinEscudoNoHayUrl() {

        Partido fuera = unico();
        fuera.setLocal(false);
        fuera.setClubRivalId(null);
        fuera.setEscudoRivalVersion(null);

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.local()).isFalse();
        assertThat(r.campo()).isEqualTo("Les Costeres Gata");
        assertThat(r.escudoRivalUrl()).isNull();
    }

    @Test
    void textoDeLaPublicacionEnEspanolYCatalanEnCasa() {

        var texto = servicio.partido(7L).textoPublicacion();

        assertThat(texto.es()).isEqualTo("Próximo partido: Mutxamel C.F. – C.D. Salesianos Alicante. Sábado 21/11 a las "
                + "17:30, Camp Nou Los Olmos (Mutxamel). ¡Os esperamos! #somMTX #mareaAzul #MutxamelCF");
        assertThat(texto.ca()).isEqualTo("Pròxim partit: Mutxamel C.F. – C.D. Salesianos Alicante. Dissabte 21/11 a les "
                + "17:30, Camp Nou Los Olmos (Mutxamel). Us esperem! #somMTX #mareaAzul #MutxamelCF");
    }

    @Test
    void textoDeLaPublicacionFueraPoneAlRivalDeLocalYElCampoRecortado() {

        unico().setLocal(false);

        var texto = servicio.partido(7L).textoPublicacion();

        assertThat(texto.es()).isEqualTo("Próximo partido: C.D. Salesianos Alicante – Mutxamel C.F. Sábado 21/11 a las "
                + "17:30, Les Costeres Gata. ¡Os esperamos! #somMTX #mareaAzul #MutxamelCF");
        assertThat(texto.ca()).contains("Pròxim partit: C.D. Salesianos Alicante – Mutxamel C.F. Dissabte 21/11 a les 17:30, "
                + "Les Costeres Gata. Us esperem!");
    }

    // ---- el logo de la competición -----------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Primera FFCV, Grup 4|/redes/logos/competiciones/primera-ffcv.png",
            "PRIMERA REGIONAL|/redes/logos/competiciones/primera-ffcv.png",
            "Segona FFCV, Grup 7|/redes/logos/competiciones/segona-ffcv.png",
            "Segunda Regional Grupo 2|/redes/logos/competiciones/segona-ffcv.png" })
    void enLigaElLogoDependeDeLaCompeticionDelPrimerEquipo(String competicion, String logo) {

        var c = CartelPrimerEquipoServiceImpl.competicion("LIGA", competicion, false);

        assertThat(c.logo()).isEqualTo(logo);
        assertThat(c.texto()).isNull();
    }

    @Test
    void enLigaSiNoEncajaSeEscribeLaCompeticionYSiNoSeSabeNoSaleNada() {

        var otra = CartelPrimerEquipoServiceImpl.competicion("LIGA", "Preferent Valenciana", false);
        assertThat(otra.logo()).isNull();
        assertThat(otra.texto()).isEqualTo("PREFERENT VALENCIANA");

        var ninguna = CartelPrimerEquipoServiceImpl.competicion("LIGA", null, false);
        assertThat(ninguna.logo()).isNull();
        assertThat(ninguna.texto()).isNull();
    }

    @Test
    void laCopaSiempreLlevaElLogoDeLaNostraCopaAunqueLaLigaSeaSegona() {

        var c = CartelPrimerEquipoServiceImpl.competicion("COPA", "Segona FFCV", false);

        assertThat(c.logo()).isEqualTo("/redes/logos/competiciones/la-nostra-copa.png");
        assertThat(c.texto()).isNull();
    }

    @Test
    void amistosoYTorneoSeEscribenEnCadaIdioma() {

        assertThat(CartelPrimerEquipoServiceImpl.competicion("AMISTOSO", null, false).texto())
                .isEqualTo("PARTIDO AMISTOSO");
        assertThat(CartelPrimerEquipoServiceImpl.competicion("AMISTOSO", null, true).texto())
                .isEqualTo("PARTIT AMISTÓS");
        assertThat(CartelPrimerEquipoServiceImpl.competicion("TORNEO", null, false).texto()).isEqualTo("TORNEO");
        assertThat(CartelPrimerEquipoServiceImpl.competicion("TORNEO", null, true).texto()).isEqualTo("TORNEIG");
        assertThat(CartelPrimerEquipoServiceImpl.competicion("AMISTOSO", "Primera FFCV", false).logo()).isNull();
    }

    @Test
    void cadaLogoDeCompeticionExisteEnResources() {

        for (String logo : List.of(CartelPrimerEquipoServiceImpl.LOGO_PRIMERA, CartelPrimerEquipoServiceImpl.LOGO_SEGONA,
                CartelPrimerEquipoServiceImpl.LOGO_COPA)) {
            assertThat(CartelPrimerEquipoServiceImpl.class.getResource(logo)).as(logo).isNotNull();
        }
    }

    @Test
    void elTipoDelPartidoLlegaAlRenderizador() {

        unico().setTipo("COPA");
        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        assertThat(datosDelUltimoRender().logoCompeticion()).endsWith("la-nostra-copa.png");
    }

    @Test
    void lasCompeticionesDelJsonSegunElTipo() {

        assertThat(servicio.partido(7L).competicion()).isEqualTo("Primera FFCV, Grup 4");

        unico().setTipo("COPA");
        assertThat(servicio.partido(7L).competicion()).isEqualTo("La Nostra Copa");
        assertThat(servicio.partido(7L).tipo()).isEqualTo("COPA");

        unico().setTipo("amistoso");
        assertThat(servicio.partido(7L).tipo()).isEqualTo("AMISTOSO");

        unico().setTipo(null);
        assertThat(servicio.partido(7L).tipo()).isEqualTo("LIGA");
    }

    // ---- la jornada ------------------------------------------------------------------------------------------

    @Test
    void laJornadaSoloSaleSiElPartidoLaTiene() {

        servicio.png(7L, "PUBLICACION", "es", true, true, true);
        assertThat(datosDelUltimoRender().jornada()).isEqualTo("JORNADA 11");

        unico().setJornada(null);
        PartidoPrimerEquipoResponse r = servicio.partido(7L);
        assertThat(r.jornada()).isNull();

        org.mockito.Mockito.clearInvocations(renderer);
        servicio.png(7L, "PUBLICACION", "es", true, true, true);
        assertThat(datosDelUltimoRender().jornada()).isNull();
    }

    // ---- local y visitante, escudos ---------------------------------------------------------------------------------

    @Test
    void enCasaMutxamelVaALaIzquierdaYElRivalALaDerechaYFueraAlReves() throws Exception {

        fondoNegro();
        when(imagenRedesDao.escudoRival(40L)).thenReturn(png(300, 300, new Color(255, 0, 255)));

        BufferedImage casa = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));
        assertThat(datosDelUltimoRender().local()).isTrue();

        // En el hueco de la derecha (625..925) está el rojo del rival; en el de la izquierda, no.
        assertThat(contarMagenta(casa, 625, 477, 300, 300)).isGreaterThan(50_000);
        assertThat(contarMagenta(casa, 155, 477, 300, 300)).isZero();

        unico().setLocal(false);
        org.mockito.Mockito.clearInvocations(renderer);

        BufferedImage fuera = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));
        assertThat(datosDelUltimoRender().local()).isFalse();

        assertThat(contarMagenta(fuera, 155, 477, 300, 300)).isGreaterThan(50_000);
        assertThat(contarMagenta(fuera, 625, 477, 300, 300)).isZero();
    }

    @Test
    void elRivalPropioPintaElEscudoDelMutxamelYNoPideEscudoDeRival() throws Exception {

        unico().setRival("Mutxamel C.F. B");

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        Datos datos = datosDelUltimoRender();
        assertThat(datos.rivalPropio()).isTrue();
        assertThat(datos.escudoRival()).isNull();
        verify(imagenRedesDao, never()).escudoRival(anyLong());
        assertThat(servicio.partido(7L).escudoRivalUrl()).isNull();
    }

    @Test
    void rivalSinEscudoSuNombreVaGrandeEnSuHuecoYNadaDebajo() throws Exception {

        fondoNegro();
        unico().setClubRivalId(null);
        unico().setEscudoRivalVersion(null);

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        assertThat(datosDelUltimoRender().escudoRival()).isNull();

        // Hueco del rival (derecha): su nombre en blanco; debajo del hueco no hay nada.
        assertThat(contarClaros(imagen, 625, 477, 300, 300)).isGreaterThan(1500);
        assertThat(contarClaros(imagen, 560, 791, 460, 98)).isZero();
        // Debajo del Mutxamel sí está su nombre.
        assertThat(contarClaros(imagen, 60, 791, 460, 98)).isGreaterThan(300);
    }

    @Test
    void rivalConEscudoLlevaSuNombreDebajoYElEscudoDelClubSeLeeUnaVezPorCartel() throws Exception {

        fondoNegro();
        when(imagenRedesDao.escudoRival(40L)).thenReturn(png(300, 300, new Color(255, 0, 255)));

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        assertThat(contarClaros(imagen, 560, 791, 460, 98)).isGreaterThan(300);
        verify(imagenRedesDao).escudoRival(40L);
    }

    @Test
    void unEscudoRivalCorruptoSeTrataComoSinEscudo() throws Exception {

        when(imagenRedesDao.escudoRival(40L)).thenReturn(new byte[] { 1, 2, 3 });

        assertThat(leer(servicio.png(7L, "PUBLICACION", "es", false, false, false)).getWidth()).isEqualTo(1080);
    }

    private static int contarMagenta(BufferedImage imagen, int x, int y, int ancho, int alto) {

        int total = 0;

        for (int j = y; j < y + alto; j++) {
            for (int i = x; i < x + ancho; i++) {
                int rgb = imagen.getRGB(i, j);
                if (((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 60 && (rgb & 0xFF) > 200) {
                    total++;
                }
            }
        }

        return total;
    }

    /** Píxeles claros (casi blancos). */
    private static int contarClaros(BufferedImage imagen, int x, int y, int ancho, int alto) {

        int total = 0;

        for (int j = y; j < y + alto; j++) {
            for (int i = x; i < x + ancho; i++) {
                int rgb = imagen.getRGB(i, j);
                if (((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) > 200 && (rgb & 0xFF) > 200) {
                    total++;
                }
            }
        }

        return total;
    }

    // ---- el campo de fuera ----------------------------------------------------------------------------------------

    private static java.awt.Graphics2D grafico() {

        java.awt.Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        ImagenesRedesRenderer.configurar(g);

        return g;
    }

    private static CartelPrimerEquipoRenderer.Ajuste campo(String texto) {

        java.awt.Font luz = ImagenesRedesRenderer.fuente("Inter-Light.otf");

        // La caja de publicación: de x=745 a 1040 (295 de ancho) y, anclada a la línea de la fecha, unos 114 de alto.
        return CartelPrimerEquipoRenderer.ajustarCampoFuera(grafico(), luz, texto, 295, 114, 135 / 3 / 1.04);
    }

    @Test
    void lesCosteresGataVaEnDosLineasEquilibradas() {

        var ajuste = campo("LES COSTERES GATA");

        assertThat(ajuste.lineas()).containsExactly("LES COSTERES", "GATA");
    }

    @Test
    void unNombreLargoRealVaEnTresLineas() {

        var ajuste = campo("CIUDAD DEPORTIVA SANT JOAN D'ALACANT");

        assertThat(ajuste.lineas()).hasSize(3);
        assertThat(String.join(" ", ajuste.lineas())).isEqualTo("CIUDAD DEPORTIVA SANT JOAN D'ALACANT");
    }

    @Test
    void unNombreCortoVaEnUnaLinea() {

        assertThat(campo("GATA").lineas()).containsExactly("GATA");
        assertThat(campo("EL PLA").lineas()).containsExactly("EL PLA");
    }

    @Test
    void conMenosLineasLaLetraNoBajaDelOchentaYCincoPorCientoDelMaximo() {

        var uno = campo("EL PLA");
        var dos = campo("LES COSTERES GATA");
        var tres = campo("CIUDAD DEPORTIVA SANT JOAN D'ALACANT");

        // El de una línea y el de dos mantienen una letra grande; el de tres, la máxima que cabe
        assertThat(uno.fuente().getSize2D()).isGreaterThan(30f);
        assertThat(dos.fuente().getSize2D()).isGreaterThan(30f);
        assertThat(tres.fuente().getSize2D()).isGreaterThan(20f);
    }

    @Test
    void alPartirEnDosLineasSeEquilibraLaLongitud() {

        var lineas = CartelPrimerEquipoRenderer.repartirEquilibrado(grafico(),
                ImagenesRedesRenderer.fuente("Inter-Light.otf").deriveFont(30f),
                new String[] { "LES", "COSTERES", "GATA" }, 2);

        assertThat(lineas).containsExactly("LES COSTERES", "GATA");

        var tres = CartelPrimerEquipoRenderer.repartirEquilibrado(grafico(),
                ImagenesRedesRenderer.fuente("Inter-Light.otf").deriveFont(30f),
                new String[] { "A", "BB", "CCC", "DDDD" }, 3);

        assertThat(tres).hasSize(3);
        assertThat(String.join(" ", tres)).isEqualTo("A BB CCC DDDD");
    }

    @Test
    void elCampoDeFueraNoSubePorEncimaDeSuCajaNiPasaDelMargenDerecho() throws Exception {

        for (String formato : new String[] { "PUBLICACION", "HISTORIA" }) {

            fondoNegro(formato);
            unico().setLocal(false);
            unico().setCampo("Campo Mpal. Les Costeres F-11 Gata");

            BufferedImage imagen = leer(servicio.png(7L, formato, "es", false, false, false));

            // PUBLICACION: caja (745,317,255,135); HISTORIA: (707,512,293,153)
            int x = formato.equals("HISTORIA") ? 707 : 745;
            int y = formato.equals("HISTORIA") ? 512 : 317;
            int alto = formato.equals("HISTORIA") ? 153 : 135;

            assertThat(contarClaros(imagen, x, y + alto / 2, 1040 - x, alto / 2 + 10)).as(formato).isGreaterThan(300);
            // Nada pasa del margen derecho (x hasta 1040) ni sube por encima de la caja de medidas.md
            assertThat(contarClaros(imagen, 1041, y, 39, alto + 20)).as(formato).isZero();
            assertThat(contarClaros(imagen, x, y - 8, 1040 - x, 8)).as(formato).isZero();
        }
    }

    @Test
    void elCampoDeFueraArrancaEnLaLineaDeLaFechaYElDeCasaNoCambia() throws Exception {

        fondoNegro();

        unico().setLocal(false);
        unico().setCampo("Campo Mpal. Les Costeres F-11 Gata");

        BufferedImage fuera = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));
        int primeraFilaCampo = primeraFilaClara(fuera, 745, 1040, 310, 470);
        int primeraFilaFecha = primeraFilaClara(fuera, 80, 282, 310, 470);

        // Mismo comienzo (la fecha lleva una tilde en "SÁBADO": se admite unos píxeles de diferencia)
        assertThat(Math.abs(primeraFilaCampo - primeraFilaFecha)).isLessThan(20);

        unico().setLocal(true);

        BufferedImage casa = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        // Casa: tres líneas fijas centradas en la caja de medidas (317..452)
        assertThat(primeraFilaClara(casa, 745, 1040, 310, 470)).isBetween(317, 345);
    }

    private static int primeraFilaClara(BufferedImage imagen, int desdeX, int hastaX, int desdeY, int hastaY) {

        for (int y = desdeY; y < hastaY; y++) {
            for (int x = desdeX; x < hastaX; x++) {
                int rgb = imagen.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) > 200 && (rgb & 0xFF) > 200) {
                    return y;
                }
            }
        }

        return -1;
    }

    private void fondoNegro(String formato) throws Exception {
        // El fondo se escala (cover) a cualquier formato: basta uno negro grande.
        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(new FondoCartel("PRIMER_EQUIPO",
                png(1080, 1920, Color.BLACK), "image/png", 1080, 1920, LocalDateTime.now(), null));
    }

    // ---- racha, qr y hashtags -------------------------------------------------------------------------------

    @Test
    void laRachaSonLosUltimosCincoDelMasAntiguoAlMasReciente() {

        // La consulta devuelve del más reciente al más antiguo.
        when(partidoDao.obtenerUltimosConResultado(1L, SABADO, 5)).thenReturn(List.of(resultado(1, 3, 0),
                resultado(2, 1, 1), resultado(3, 0, 2), resultado(4, 2, 1), resultado(5, 4, 4)));

        servicio.png(7L, "PUBLICACION", "es", true, false, false);

        assertThat(datosDelUltimoRender().racha()).containsExactly("E", "G", "P", "E", "G");
    }

    @Test
    void conMenosDeCincoPartidosSalenLosQueHay() {

        when(partidoDao.obtenerUltimosConResultado(1L, SABADO, 5))
                .thenReturn(List.of(resultado(1, 1, 0), resultado(2, 0, 1)));

        servicio.png(7L, "PUBLICACION", "es", true, false, false);

        assertThat(datosDelUltimoRender().racha()).containsExactly("P", "G");
    }

    @Test
    void sinLaOpcionDeRachaNoSeConsultaNiSePinta() {

        servicio.png(7L, "PUBLICACION", "es", false, true, true);

        assertThat(datosDelUltimoRender().racha()).isEmpty();
        verify(partidoDao, never()).obtenerUltimosConResultado(anyLong(), any(), anyInt());
    }

    private record Region(int minX, int maxX, int minY, int maxY, int pixeles) {
    }

    /** La caja que ocupan los píxeles que no son negros en la zona de abajo del cartel (racha, hashtags y QR). */
    private static Region contenidoInferior(BufferedImage imagen) {

        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int minY = Integer.MAX_VALUE;
        int maxY = -1;
        int pixeles = 0;

        for (int y = 900; y < 1070; y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int rgb = imagen.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) > 60) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    pixeles++;
                }
            }
        }

        return new Region(minX, maxX, minY, maxY, pixeles);
    }

    private BufferedImage cartel(boolean racha, boolean qr, boolean hashtags) throws Exception {

        when(partidoDao.obtenerUltimosConResultado(1L, SABADO, 5)).thenReturn(List.of(resultado(1, 3, 0),
                resultado(2, 1, 1), resultado(3, 0, 2), resultado(4, 2, 1), resultado(5, 4, 0)));

        return leer(servicio.png(7L, "PUBLICACION", "es", racha, qr, hashtags));
    }

    @Test
    void sinNingunOpcionalNoSeDibujaNadaEnElBloqueDeAbajo() throws Exception {

        fondoNegro();

        assertThat(contenidoInferior(cartel(false, false, false)).pixeles()).isZero();
    }

    @Test
    void conLosTresOpcionalesOcupanElAnchoDeLaReferenciaCentrados() throws Exception {

        fondoNegro();

        Region todo = contenidoInferior(cartel(true, true, true));

        // Referencia: de x=167 (bloque) a x=913 (QR), centrado en 540.
        assertThat(todo.minX()).isBetween(160, 180);
        assertThat(todo.maxX()).isBetween(900, 920);
        assertThat((todo.minX() + todo.maxX()) / 2).isBetween(530, 550);
    }

    @Test
    void sinQrElBloqueSeCentraConLoQueQuedaYSinHuecoALaDerecha() throws Exception {

        fondoNegro();

        for (boolean racha : new boolean[] { true, false }) {

            boolean hashtags = true;
            Region r = contenidoInferior(cartel(racha, false, hashtags));

            assertThat(r.pixeles()).isGreaterThan(0);
            assertThat((r.minX() + r.maxX()) / 2).as("racha=" + racha).isBetween(530, 550);
            // El QR (esquina derecha de la referencia) no está.
            assertThat(r.maxX()).isLessThan(840);
        }
    }

    @Test
    void soloElQrSeCentraEnLaFila() throws Exception {

        fondoNegro();

        Region qr = contenidoInferior(cartel(false, true, false));

        assertThat(qr.pixeles()).isGreaterThan(5_000);
        assertThat((qr.minX() + qr.maxX()) / 2).isBetween(530, 550);
        assertThat(qr.maxX() - qr.minX()).isBetween(100, 160);
    }

    @Test
    void soloLosHashtagsVanCentradosYMasAnchosQueElQr() throws Exception {

        fondoNegro();

        Region hashtags = contenidoInferior(cartel(false, false, true));

        assertThat((hashtags.minX() + hashtags.maxX()) / 2).isBetween(530, 550);
        assertThat(hashtags.maxX() - hashtags.minX()).isBetween(500, 580);
    }

    @Test
    void soloLaRachaTieneLosCincoCirculosDeLosColoresDeLaApp() throws Exception {

        fondoNegro();

        BufferedImage imagen = cartel(true, false, false);
        Region r = contenidoInferior(imagen);

        assertThat((r.minX() + r.maxX()) / 2).isBetween(530, 550);
        assertThat(contarColor(imagen, 0x2E9E4F)).isGreaterThan(3000);
        assertThat(contarColor(imagen, 0x8A949B)).isGreaterThan(1000);
        assertThat(contarColor(imagen, 0xD64545)).isGreaterThan(1000);
    }

    @Test
    void sinRachaNoHayCirculosAunqueHayaHashtagsYQr() throws Exception {

        fondoNegro();

        BufferedImage imagen = cartel(false, true, true);

        assertThat(contarColor(imagen, 0x2E9E4F)).isZero();
        assertThat(contarColor(imagen, 0xD64545)).isZero();
    }

    @Test
    void sinNingunPartidoConResultadoLaRachaNoDejaHueco() throws Exception {

        fondoNegro();
        when(partidoDao.obtenerUltimosConResultado(1L, SABADO, 5)).thenReturn(List.of());

        // Solo racha pedida y sin datos: no se dibuja nada.
        assertThat(contenidoInferior(leer(servicio.png(7L, "PUBLICACION", "es", true, false, false))).pixeles()).isZero();
    }

    private static int contarColor(BufferedImage imagen, int rgbEsperado) {

        int total = 0;

        for (int y = 900; y < 1070; y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int rgb = imagen.getRGB(x, y) & 0xFFFFFF;
                if (Math.abs(((rgb >> 16) & 0xFF) - ((rgbEsperado >> 16) & 0xFF)) < 8
                        && Math.abs(((rgb >> 8) & 0xFF) - ((rgbEsperado >> 8) & 0xFF)) < 8
                        && Math.abs((rgb & 0xFF) - (rgbEsperado & 0xFF)) < 8) {
                    total++;
                }
            }
        }

        return total;
    }

    // ---- formatos, idiomas y parámetros ---------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({ "PUBLICACION,es,1080,1350", "PUBLICACION,ca,1080,1350", "HISTORIA,es,1080,1920",
            "HISTORIA,ca,1080,1920", "publicacion,CA,1080,1350" })
    void elPngTieneElTamanoDelFormatoYEsValidoEnAmbosIdiomas(String formato, String idioma, int ancho, int alto)
            throws Exception {

        byte[] png = servicio.png(7L, formato, idioma, true, true, true);

        // Cabecera PNG
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        BufferedImage imagen = leer(png);

        assertThat(imagen.getWidth()).isEqualTo(ancho);
        assertThat(imagen.getHeight()).isEqualTo(alto);
    }

    @Test
    void porDefectoPublicacionYEspanol() throws Exception {

        BufferedImage imagen = leer(servicio.png(7L, null, null, true, true, true));

        assertThat(imagen.getHeight()).isEqualTo(1350);
        assertThat(datosDelUltimoRender().titulo()).isEqualTo("PRÓXIMO PARTIDO");
    }

    @Test
    void losTextosCambianDeIdioma() {

        servicio.png(7L, "PUBLICACION", "es", true, true, true);
        Datos es = datosDelUltimoRender();

        assertThat(es.titulo()).isEqualTo("PRÓXIMO PARTIDO");
        assertThat(es.dia()).isEqualTo("SÁBADO");
        assertThat(es.fecha()).isEqualTo("21/11/2026");
        assertThat(es.hora()).isEqualTo("17:30H");
        assertThat(es.textoRacha()).isEqualTo("ÚLTIMOS 5");
        assertThat(es.textoQr()).isEqualTo("Descarga la app");
        assertThat(es.campo()).isEqualTo("CAMP NOU\nLOS OLMOS\n(MUTXAMEL)");
        assertThat(es.nombreMutxamel()).isEqualTo("MUTXAMEL C.F.");
        assertThat(es.nombreRival()).isEqualTo("C.D. SALESIANOS ALICANTE");

        org.mockito.Mockito.clearInvocations(renderer);
        servicio.png(7L, "PUBLICACION", "ca", true, true, true);
        Datos ca = datosDelUltimoRender();

        assertThat(ca.titulo()).isEqualTo("PRÒXIM PARTIT");
        assertThat(ca.dia()).isEqualTo("DISSABTE");
        assertThat(ca.textoRacha()).isEqualTo("ÚLTIMS 5");
        assertThat(ca.textoQr()).isEqualTo("Descarrega l'app");
    }

    @ParameterizedTest
    @CsvSource({ "2026-11-23,DILLUNS,LUNES", "2026-11-24,DIMARTS,MARTES", "2026-11-25,DIMECRES,MIÉRCOLES",
            "2026-11-26,DIJOUS,JUEVES", "2026-11-27,DIVENDRES,VIERNES", "2026-11-28,DISSABTE,SÁBADO",
            "2026-11-29,DIUMENGE,DOMINGO" })
    void losSieteDiasEnCadaIdioma(String dia, String ca, String es) {

        LocalDate fecha = LocalDate.parse(dia);

        assertThat(CartelPrimerEquipoServiceImpl.nombreDia(fecha, true)).isEqualTo(ca);
        assertThat(CartelPrimerEquipoServiceImpl.nombreDia(fecha, false)).isEqualTo(es);
    }

    @Test
    void campoFueraRecortadoEnElCartelYSinHoraNoSaleHora() {

        Partido p = unico();
        p.setLocal(false);
        p.setHora(null);

        servicio.png(7L, "PUBLICACION", "es", true, true, true);

        Datos datos = datosDelUltimoRender();
        assertThat(datos.campo()).isEqualTo("LES COSTERES GATA");
        assertThat(datos.hora()).isEmpty();
        assertThat(datos.local()).isFalse();
    }

    @Test
    void unFormatoOUnIdiomaNoValidoEsUnError() {

        assertThatThrownBy(() -> servicio.png(7L, "CUADRADO", "es", true, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PUBLICACION o HISTORIA");
        assertThatThrownBy(() -> servicio.png(7L, "PUBLICACION", "en", true, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("es o ca");
    }

    // ---- el fondo ------------------------------------------------------------------------------------------------

    @Test
    void elFondoDeLaTablaSustituyeAlPorDefectoYAlRestaurarVuelveElDeResources() throws Exception {

        byte[] porDefecto = servicio.png(7L, "PUBLICACION", "es", false, false, false);

        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(new FondoCartel("PRIMER_EQUIPO",
                png(1200, 1500, new Color(0, 0, 255)), "image/png", 1200, 1500, LocalDateTime.now(), "admin"));

        BufferedImage personalizado = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        // A media altura y en el borde izquierdo no hay nada dibujado: es el azul del fondo subido.
        assertThat(personalizado.getRGB(3, 700) & 0xFFFFFF).isEqualTo(0x0000FF);
        assertThat(leer(porDefecto).getRGB(3, 700) & 0xFFFFFF).isNotEqualTo(0x0000FF);

        // Restaurar: ya no hay fila.
        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(null);

        BufferedImage restaurado = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));
        assertThat(restaurado.getRGB(3, 700)).isEqualTo(leer(porDefecto).getRGB(3, 700));
    }

    @Test
    void unFondoMuyClaroOscureceArribaYAbajoComoMuchoUnTreintaYCincoPorCiento() throws Exception {

        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(new FondoCartel("PRIMER_EQUIPO",
                png(1080, 1350, Color.WHITE), "image/png", 1080, 1350, LocalDateTime.now(), null));

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        // Arriba del todo (sin texto en x=2) el blanco queda al 65 % como mínimo; a media altura no se toca.
        int arriba = imagen.getRGB(2, 2) & 0xFF;
        int medio = imagen.getRGB(2, 700) & 0xFF;
        int abajo = imagen.getRGB(2, 1347) & 0xFF;

        assertThat(medio).isEqualTo(255);
        assertThat(arriba).isBetween((int) (255 * 0.64), 255 - 30);
        assertThat(abajo).isBetween((int) (255 * 0.64), 255 - 30);
    }

    @Test
    void unFondoOscuroNoSeOscurece() {

        assertThat(CartelPrimerEquipoRenderer.oscuridadNecesaria(0.2)).isZero();
        assertThat(CartelPrimerEquipoRenderer.oscuridadNecesaria(0.40)).isZero();
        assertThat(CartelPrimerEquipoRenderer.oscuridadNecesaria(0.75)).isEqualTo(0.35);
        assertThat(CartelPrimerEquipoRenderer.oscuridadNecesaria(1.0)).isEqualTo(0.35);
        assertThat(CartelPrimerEquipoRenderer.oscuridadNecesaria(0.575)).isBetween(0.15, 0.20);
    }

    @Test
    void losPatrocinadoresSalenAbajoEnDosFilasYElDestacadoArriba() throws Exception {

        fondoNegro();

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        // El bloque de patrocinadores (40,1124,1000,200) tiene logos blancos en las dos filas.
        assertThat(contarClaros(imagen, 40, 1124, 1000, 100)).isGreaterThan(3000);
        assertThat(contarClaros(imagen, 40, 1224, 1000, 100)).isGreaterThan(3000);
        // Y el destacado (ayuntamiento) arriba a la izquierda.
        assertThat(contarClaros(imagen, 40, 34, 185, 92)).isGreaterThan(500);
    }

    // ---- ejemplos para revisar el diseño ----------------------------------------------------------------------------

    /**
     * Genera los PNG de ejemplo de docs/diseno-cartel-primer-equipo/ejemplos con el flujo real (servicio y renderizador):
     * publicación e historia, en casa y fuera, en castellano y catalán.
     */
    @Test
    void generaLosEjemplosParaRevisarElDiseno() throws Exception {

        Path carpeta = Path.of("..", "docs", "diseno-cartel-primer-equipo", "ejemplos");
        Files.createDirectories(carpeta);

        when(imagenRedesDao.escudoRival(40L)).thenReturn(escudoDeEjemplo());
        when(partidoDao.obtenerUltimosConResultado(1L, SABADO, 5)).thenReturn(List.of(resultado(1, 3, 0),
                resultado(2, 1, 1), resultado(3, 0, 2), resultado(4, 2, 1), resultado(5, 4, 0)));

        Partido p = unico();

        escribir(carpeta.resolve("publicacion-casa-es.png"), servicio.png(7L, "PUBLICACION", "es", true, true, true));
        escribir(carpeta.resolve("historia-casa-ca.png"), servicio.png(7L, "HISTORIA", "ca", true, true, true));

        p.setLocal(false);
        p.setTipo("COPA");
        p.setJornada(null);

        escribir(carpeta.resolve("publicacion-fuera-ca.png"), servicio.png(7L, "PUBLICACION", "ca", true, true, true));
        escribir(carpeta.resolve("historia-fuera-es.png"), servicio.png(7L, "HISTORIA", "es", true, true, true));

        // Un escudo de verdad en el lado del rival (no hay escudos de rivales en el repositorio: se usa el del Mutxamel como
        // si fuera el del rival) para ver cómo queda a ese tamaño y con la sombra.
        try (var entrada = CartelPrimerEquipoServiceImplTest.class.getResourceAsStream("/redes/escudo.png")) {
            when(imagenRedesDao.escudoRival(40L)).thenReturn(entrada.readAllBytes());
        }

        p.setTipo("LIGA");
        p.setJornada(11);

        escribir(carpeta.resolve("publicacion-fuera-es-escudo-real.png"),
                servicio.png(7L, "PUBLICACION", "es", true, true, true));

        for (String nombre : List.of("publicacion-casa-es", "historia-casa-ca", "publicacion-fuera-ca",
                "historia-fuera-es", "publicacion-fuera-es-escudo-real")) {
            assertThat(leer(Files.readAllBytes(carpeta.resolve(nombre + ".png"))).getWidth()).isEqualTo(1080);
        }
    }

    private static void escribir(Path ruta, byte[] png) throws Exception {
        Files.write(ruta, png);
    }

    /** Un escudo inventado (círculo rojo con anillo blanco) para los ejemplos. */
    private static byte[] escudoDeEjemplo() throws Exception {

        BufferedImage imagen = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillOval(4, 4, 248, 248);
        g.setColor(new Color(0xC62828));
        g.fillOval(22, 22, 212, 212);
        g.setColor(Color.WHITE);
        g.fillOval(88, 60, 80, 80);
        g.fillRoundRect(70, 140, 116, 60, 30, 30);
        g.dispose();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);

        return salida.toByteArray();
    }

    @Test
    void elCartelNoContieneNombresNiFotosDeJugadores() {

        // El renderizador solo recibe equipo, rival, fecha, campo, racha y textos: ningún dato de jugadores.
        for (var campo : Datos.class.getRecordComponents()) {
            assertThat(campo.getName().toLowerCase()).doesNotContain("jugador").doesNotContain("foto");
        }

        assertThat(List.of(Formato.values())).hasSize(2);
        assertThat(new ArrayList<>(List.of(Formato.PUBLICACION.ancho(), Formato.HISTORIA.alto()))).containsExactly(1080,
                1920);
    }
}
