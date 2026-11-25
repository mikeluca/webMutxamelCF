package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
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
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.FondoCartel;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CartelNoDisponibleException;
import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Datos;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;

/** El cartel "Próximo partido del Filial": qué partido, sus datos y el PNG que sale (con el renderizador real). */
class CartelFilialServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // Jueves 19/11/2026, 12:00 en Madrid
    private static final Instant AHORA = Instant.parse("2026-11-19T11:00:00Z");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 22);

    private final EquipoGestionDao gestion = mock(EquipoGestionDao.class);
    private final EquipoDao equipoDao = mock(EquipoDao.class);
    private final PartidoDao partidoDao = mock(PartidoDao.class);
    private final EquipoFfcvDao ffcvDao = mock(EquipoFfcvDao.class);
    private final TemporadaDao temporadaDao = mock(TemporadaDao.class);
    private final ImagenRedesDao imagenRedesDao = mock(ImagenRedesDao.class);
    private final ImagenRedesFondoDao fondoDao = mock(ImagenRedesFondoDao.class);
    private final PartidoLiveEventoDao eventoDao = mock(PartidoLiveEventoDao.class);
    private final CartelFilialRenderer renderer = spy(new CartelFilialRenderer());

    private CartelFilialServiceImpl servicio;
    private EquipoFfcv ffcv;

    @BeforeEach
    void setUp() {

        servicio = new CartelFilialServiceImpl(gestion, equipoDao, partidoDao, ffcvDao, temporadaDao, imagenRedesDao,
                eventoDao, new FondosCartelesServiceImpl(fondoDao), renderer, Clock.fixed(AHORA, MADRID));

        // 7 es Retransmisión, 9 administrador de la app, 5 (un coordinador) no tiene acceso.
        when(gestion.esRrssOAdmin(7L)).thenReturn(true);
        when(gestion.esRrssOAdmin(9L)).thenReturn(true);

        Equipo filial = new Equipo();
        filial.setId(2L);
        when(equipoDao.obtenerEquipoPorNombre("Filial")).thenReturn(filial);

        Temporada temporada = new Temporada();
        temporada.setId(3L);
        temporada.setFechaInicio(Date.valueOf("2026-07-01"));
        temporada.setFechaFin(Date.valueOf("2027-06-30"));
        when(temporadaDao.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada);

        ffcv = new EquipoFfcv();
        ffcv.setCompeticion("Segona FFCV, Grup 7");
        when(ffcvDao.obtenerPorEquipoYTemporada(2L, 3L)).thenReturn(ffcv);

        when(partidoDao.obtenerPorEquipo(2L)).thenReturn(List.of(partido(40L, DOMINGO, "19:00")));
    }

    private static Partido partido(long id, LocalDate dia, String hora) {

        Partido p = new Partido();
        p.setId(id);
        p.setEquipoId(2L);
        p.setRival("C.D. Salesianos");
        p.setDia(Date.valueOf(dia));
        p.setHora(hora);
        p.setCampo("Camp Nou Los Olmos Mutxamel F-11");
        p.setTipo("LIGA");
        p.setLocal(true);
        p.setClubRivalId(40L);
        p.setEscudoRivalVersion(2L);

        return p;
    }

    private Partido unico() {
        return partidoDao.obtenerPorEquipo(2L).get(0);
    }

    private static Partido resultado(long id, int favor, int contra) {

        Partido p = partido(id, DOMINGO.minusDays(id * 7), "19:00");
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

    // ---- qué partido (el del equipo "Filial") --------------------------------------------------------------

    @Test
    void sePideElEquipoFilialYNoElPrimerEquipo() {

        servicio.partido(7L);

        verify(equipoDao).obtenerEquipoPorNombre("Filial");
        verify(equipoDao, never()).obtenerEquipoPorNombre("Primer Equipo");
        verify(partidoDao).obtenerPorEquipo(2L);
    }

    @Test
    void sinEquipoFilialOSinPartidosEsSinPartido() {

        when(partidoDao.obtenerPorEquipo(2L)).thenReturn(List.of());
        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");

        when(equipoDao.obtenerEquipoPorNombre("Filial")).thenReturn(null);
        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");

        assertThatThrownBy(() -> servicio.png(7L, "PUBLICACION", "es", true, true, true))
                .isInstanceOfSatisfying(CartelNoDisponibleException.class,
                        e -> assertThat(e.motivo()).isEqualTo("SIN_PARTIDO"));
    }

    @Test
    void unPartidoDeHoyTerminadoSeSaltaYSeEligeElSiguiente() {

        Partido terminado = partido(1L, LocalDate.of(2026, 11, 19), "10:00");
        terminado.setGolesFavor(2);
        terminado.setGolesContra(1);

        when(partidoDao.obtenerPorEquipo(2L))
                .thenReturn(List.of(terminado, partido(2L, LocalDate.of(2026, 11, 29), "19:00")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void unPartidoEnJuegoEsYaEmpezadoYUnoDeHaceCuatroHorasSeDaPorTerminado() {

        when(partidoDao.obtenerPorEquipo(2L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "11:00"),
                partido(2L, LocalDate.of(2026, 11, 29), "19:00")));

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isFalse();
        assertThat(r.motivo()).isEqualTo("YA_EMPEZADO");

        when(partidoDao.obtenerPorEquipo(2L)).thenReturn(List.of(partido(1L, LocalDate.of(2026, 11, 19), "08:00"),
                partido(2L, LocalDate.of(2026, 11, 29), "19:00")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void unPartidoCanceladoSeSaltaYSinMasPartidosEsSinPartido() {

        Partido cancelado = partido(1L, LocalDate.of(2026, 11, 20), "19:00");
        cancelado.setCancelado(true);

        when(partidoDao.obtenerPorEquipo(2L)).thenReturn(List.of(cancelado));

        assertThat(servicio.partido(7L).motivo()).isEqualTo("SIN_PARTIDO");

        when(partidoDao.obtenerPorEquipo(2L))
                .thenReturn(List.of(cancelado, partido(2L, LocalDate.of(2026, 11, 28), "19:00")));

        assertThat(servicio.partido(7L).partidoId()).isEqualTo(2L);
    }

    @Test
    void laRespuestaTieneElContratoDelPrimerEquipoYElTextoConElNombreDelFilial() {

        PartidoPrimerEquipoResponse r = servicio.partido(7L);

        assertThat(r.disponible()).isTrue();
        assertThat(r.partidoId()).isEqualTo(40L);
        assertThat(r.local()).isTrue();
        assertThat(r.rival()).isEqualTo("C.D. Salesianos");
        assertThat(r.escudoRivalUrl()).isEqualTo("/api/public/escudos-rivales/40?v=2");
        assertThat(r.dia()).isEqualTo(DOMINGO);
        assertThat(r.hora()).isEqualTo("19:00");
        assertThat(r.campo()).isEqualTo("Camp Nou Los Olmos (Mutxamel)");
        assertThat(r.competicion()).isEqualTo("Segona FFCV, Grup 7");
        assertThat(r.tipo()).isEqualTo("LIGA");
        assertThat(r.textoPublicacion().es()).isEqualTo("Próximo partido: Mutxamel C.F. “B” – C.D. Salesianos. "
                + "Domingo 22/11 a las 19:00, Camp Nou Los Olmos (Mutxamel). ¡Os esperamos! #somMTX #mareaAzul #MutxamelCF");
        assertThat(r.textoPublicacion().ca()).isEqualTo("Pròxim partit: Mutxamel C.F. “B” – C.D. Salesianos. "
                + "Diumenge 22/11 a les 19:00, Camp Nou Los Olmos (Mutxamel). Us esperem! #somMTX #mareaAzul #MutxamelCF");
    }

    @Test
    void fueraElRivalVaDeLocalEnElTexto() {

        unico().setLocal(false);
        unico().setCampo("Campo Mpal. Les Costeres F-11 Gata");

        assertThat(servicio.partido(7L).textoPublicacion().es()).startsWith(
                "Próximo partido: C.D. Salesianos – Mutxamel C.F. “B”. Domingo 22/11 a las 19:00, Les Costeres Gata.");
    }

    // ---- el logo de la competición ----------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Segona FFCV, Grup 7|/redes/logos/competiciones/segona-ffcv.png",
            "Segunda Regional|/redes/logos/competiciones/segona-ffcv.png",
            "Primera FFCV, Grup 4|/redes/logos/competiciones/primera-ffcv.png" })
    void enLigaElLogoDependeDeLaCompeticionDelFilial(String competicion, String logo) {

        ffcv.setCompeticion(competicion);

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        assertThat(datosDelUltimoRender().logoCompeticion()).isEqualTo(logo);
        assertThat(datosDelUltimoRender().textoCompeticion()).isNull();
    }

    @Test
    void laCopaLlevaElLogoDeLaNostraCopa() {

        unico().setTipo("COPA");

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        assertThat(datosDelUltimoRender().logoCompeticion()).isEqualTo("/redes/logos/competiciones/la-nostra-copa.png");
    }

    @Test
    void siNoEncajaSeEscribeLaCompeticionYAmistosoYTorneoTambien() {

        ffcv.setCompeticion("Preferent Valenciana");

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        assertThat(datosDelUltimoRender().logoCompeticion()).isNull();
        assertThat(datosDelUltimoRender().textoCompeticion()).isEqualTo("PREFERENT VALENCIANA");

        clearInvocations(renderer);
        unico().setTipo("AMISTOSO");
        servicio.png(7L, "PUBLICACION", "ca", false, false, false);

        assertThat(datosDelUltimoRender().textoCompeticion()).isEqualTo("PARTIT AMISTÓS");
    }

    // ---- lados, textos e idiomas ----------------------------------------------------------------------------

    @Test
    void enCasaElMutxamelVaALaIzquierdaYElCampoEsCampNouLosOlmos() {

        servicio.png(7L, "PUBLICACION", "es", true, true, true);

        Datos d = datosDelUltimoRender();

        assertThat(d.local()).isTrue();
        assertThat(d.nombreMutxamel()).isEqualTo("MUTXAMEL C.F. “B”");
        assertThat(d.nombreRival()).isEqualTo("C.D. SALESIANOS");
        assertThat(d.campo()).isEqualTo("CAMP NOU LOS OLMOS");
        assertThat(d.diaFecha()).isEqualTo("DOMINGO 22/11/2026");
        assertThat(d.hora()).isEqualTo("19:00H");
    }

    @Test
    void enCasaEnElHogarProvincialConGuionPegadoElCampoSaleConGuionSeparado() {

        unico().setCampo("Hogar Provincial -Diputacion de Alicante- F-11");

        servicio.png(7L, "PUBLICACION", "es", true, true, true);

        assertThat(datosDelUltimoRender().campo()).isEqualTo("HOGAR PROVINCIAL - DIPUTACION DE ALICANTE");
        assertThat(servicio.partido(7L).campo()).isEqualTo("Hogar Provincial - Diputacion de Alicante");
    }

    @Test
    void enCasaEnElHogarProvincialElCampoEsElDelPartidoRecortado() {

        unico().setCampo("Campo Mpal. Hogar Provincial F-11");

        servicio.png(7L, "PUBLICACION", "es", true, true, true);

        Datos d = datosDelUltimoRender();

        assertThat(d.local()).isTrue();
        assertThat(d.campo()).isEqualTo("HOGAR PROVINCIAL");
        assertThat(servicio.partido(7L).campo()).isEqualTo("Hogar Provincial");
    }

    @Test
    void fueraElMutxamelVaALaDerechaYElCampoSeRecorta() {

        unico().setLocal(false);
        unico().setCampo("Campo Mpal. Les Costeres F-11 Gata");

        servicio.png(7L, "PUBLICACION", "es", true, true, true);

        Datos d = datosDelUltimoRender();

        assertThat(d.local()).isFalse();
        assertThat(d.campo()).isEqualTo("LES COSTERES GATA");
    }

    @Test
    void esYCaTextosYDias() {

        servicio.png(7L, "PUBLICACION", "es", true, true, true);
        Datos es = datosDelUltimoRender();

        assertThat(es.diaFecha()).startsWith("DOMINGO");
        assertThat(es.textoRacha()).isEqualTo("ÚLTIMOS 5");
        assertThat(es.textoQr()).isEqualTo("Descarga la app");

        clearInvocations(renderer);
        servicio.png(7L, "PUBLICACION", "ca", true, true, true);
        Datos ca = datosDelUltimoRender();

        assertThat(ca.diaFecha()).startsWith("DIUMENGE");
        assertThat(ca.textoRacha()).isEqualTo("ÚLTIMS 5");
        assertThat(ca.textoQr()).isEqualTo("Descarrega l'app");
    }

    @Test
    void laRachaEsLaDelFilialDelMasAntiguoAlMasReciente() {

        when(partidoDao.obtenerUltimosConResultado(2L, DOMINGO, 5)).thenReturn(List.of(resultado(1, 3, 0),
                resultado(2, 1, 1), resultado(3, 0, 2), resultado(4, 2, 1), resultado(5, 4, 4)));

        servicio.png(7L, "PUBLICACION", "es", true, false, false);

        assertThat(datosDelUltimoRender().racha()).containsExactly("E", "G", "P", "E", "G");

        clearInvocations(renderer);
        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        assertThat(datosDelUltimoRender().racha()).isEmpty();
    }

    @Test
    void unFormatoOUnIdiomaNoValidoEsUnError() {

        assertThatThrownBy(() -> servicio.png(7L, "CUADRADO", "es", true, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PUBLICACION o HISTORIA");
        assertThatThrownBy(() -> servicio.png(7L, "PUBLICACION", "en", true, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("es o ca");
    }

    @ParameterizedTest
    @CsvSource({ "PUBLICACION,es,1080,1350", "PUBLICACION,ca,1080,1350", "HISTORIA,es,1080,1920",
            "HISTORIA,ca,1080,1920" })
    void elPngTieneElTamanoDelFormatoYEsValido(String formato, String idioma, int ancho, int alto) throws Exception {

        byte[] png = servicio.png(7L, formato, idioma, true, true, true);

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        BufferedImage imagen = leer(png);

        assertThat(imagen.getWidth()).isEqualTo(ancho);
        assertThat(imagen.getHeight()).isEqualTo(alto);
    }

    @Test
    void sinHoraNoSaleHoraYElRivalPropioNoPideEscudo() {

        unico().setHora(null);
        unico().setRival("Mutxamel C.F. A");

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        Datos d = datosDelUltimoRender();

        assertThat(d.hora()).isEmpty();
        assertThat(d.rivalPropio()).isTrue();
        verify(imagenRedesDao, never()).escudoRival(anyLong());
    }

    // ---- el fondo ---------------------------------------------------------------------------------------------------

    @Test
    void sinFilaEnLaTablaSeUsaElFondoDelFilialDeResourcesYSePideElCartelFilial() throws Exception {

        servicio.png(7L, "PUBLICACION", "es", false, false, false);

        verify(fondoDao).obtener("FILIAL");
        verify(fondoDao, never()).obtener("PRIMER_EQUIPO");

        ArgumentCaptor<byte[]> fondo = ArgumentCaptor.forClass(byte[].class);
        verify(renderer).renderizar(any(), any(), fondo.capture());

        // El fondo por defecto del Filial (1079×1350)
        assertThat(leer(fondo.getValue()).getWidth()).isEqualTo(1079);
    }

    @Test
    void elFondoDeLaTablaSustituyeAlPorDefectoYElDegradadoAzulSeAplicaSiempre() throws Exception {

        // Una foto subida desde el panel: toda roja y de 1200×1500. Sin degradado se vería roja hasta abajo.
        when(fondoDao.obtener("FILIAL")).thenReturn(new FondoCartel("FILIAL", png(1200, 1500, new Color(255, 0, 0)),
                "image/png", 1200, 1500, LocalDateTime.now(), "admin"));

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        // Arriba (sin texto en x=3) se ve la foto subida
        assertThat(imagen.getRGB(3, 300) & 0xFFFFFF).isEqualTo(0xFF0000);
        // A partir del 66 % de la altura de la foto (891) es azul #004AAB opaco, sea cual sea la foto
        assertThat(imagen.getRGB(3, 900) & 0xFFFFFF).isEqualTo(0x004AAB);
        assertThat(imagen.getRGB(3, 1340) & 0xFFFFFF).isEqualTo(0x004AAB);
        // Por debajo del 55 % (742) la foto está intacta; entre el 55 y el 66 % se mezcla
        assertThat(imagen.getRGB(3, 740) & 0xFFFFFF).isEqualTo(0xFF0000);
        int mezcla = imagen.getRGB(3, 820) & 0xFFFFFF;
        assertThat(mezcla).isNotEqualTo(0xFF0000).isNotEqualTo(0x004AAB);
    }

    @Test
    void enHistoriaLaFotoOcupaArribaYDebajoHayAzulLiso() throws Exception {

        when(fondoDao.obtener("FILIAL")).thenReturn(new FondoCartel("FILIAL", png(1200, 1500, new Color(255, 0, 0)),
                "image/png", 1200, 1500, LocalDateTime.now(), "admin"));

        BufferedImage imagen = leer(servicio.png(7L, "HISTORIA", "es", false, false, false));

        assertThat(imagen.getHeight()).isEqualTo(1920);
        assertThat(imagen.getRGB(3, 300) & 0xFFFFFF).isEqualTo(0xFF0000);
        assertThat(imagen.getRGB(3, 1349) & 0xFFFFFF).isEqualTo(0x004AAB);
        assertThat(imagen.getRGB(3, 1400) & 0xFFFFFF).isEqualTo(0x004AAB);
        assertThat(imagen.getRGB(3, 1915) & 0xFFFFFF).isEqualTo(0x004AAB);
    }

    @Test
    void alRestaurarVuelveElFondoPorDefecto() throws Exception {

        when(fondoDao.obtener("FILIAL")).thenReturn(new FondoCartel("FILIAL", png(1200, 1500, new Color(255, 0, 0)),
                "image/png", 1200, 1500, LocalDateTime.now(), null));
        BufferedImage personalizado = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        when(fondoDao.obtener("FILIAL")).thenReturn(null);
        BufferedImage porDefecto = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        assertThat(personalizado.getRGB(3, 300) & 0xFFFFFF).isEqualTo(0xFF0000);
        assertThat(porDefecto.getRGB(3, 300) & 0xFFFFFF).isNotEqualTo(0xFF0000);
    }

    @Test
    void elFondoDelPrimerEquipoNoAfectaAlCartelDelFilial() throws Exception {

        when(fondoDao.obtener("PRIMER_EQUIPO")).thenReturn(new FondoCartel("PRIMER_EQUIPO",
                png(1200, 1500, new Color(255, 0, 0)), "image/png", 1200, 1500, LocalDateTime.now(), null));

        BufferedImage imagen = leer(servicio.png(7L, "PUBLICACION", "es", false, false, false));

        assertThat(imagen.getRGB(3, 300) & 0xFFFFFF).isNotEqualTo(0xFF0000);
    }

    // ---- ejemplos para revisar el diseño ----------------------------------------------------------------------------

    /**
     * Genera los PNG de ejemplo de docs/diseno-cartel-filial/ejemplos con el flujo real (servicio y renderizador).
     */
    @Test
    void generaLosEjemplosParaRevisarElDiseno() throws Exception {

        Path carpeta = Path.of("..", "docs", "diseno-cartel-filial", "ejemplos");
        Files.createDirectories(carpeta);

        when(imagenRedesDao.escudoRival(40L)).thenReturn(escudoDeEjemplo());
        when(partidoDao.obtenerUltimosConResultado(2L, DOMINGO, 5)).thenReturn(List.of(resultado(1, 3, 0),
                resultado(2, 1, 1), resultado(3, 0, 2), resultado(4, 2, 1), resultado(5, 4, 0)));

        Partido p = unico();

        escribir(carpeta.resolve("publicacion-casa-es.png"), servicio.png(7L, "PUBLICACION", "es", true, true, true));
        escribir(carpeta.resolve("historia-casa-es.png"), servicio.png(7L, "HISTORIA", "es", true, true, true));
        escribir(carpeta.resolve("publicacion-sin-opcionales.png"),
                servicio.png(7L, "PUBLICACION", "es", false, false, false));

        p.setLocal(false);
        p.setRival("Alicante Coty FC");
        p.setCampo("Campo de Fútbol Municipal Pedro Ferrándiz Gran Alacant F-11 C.A.");

        escribir(carpeta.resolve("publicacion-fuera-ca-rival-largo.png"),
                servicio.png(7L, "PUBLICACION", "ca", true, true, true));

        p.setLocal(true);
        p.setRival("C.D. Salesianos");
        p.setTipo("COPA");

        escribir(carpeta.resolve("publicacion-copa.png"), servicio.png(7L, "PUBLICACION", "es", true, true, true));

        for (String nombre : List.of("publicacion-casa-es", "historia-casa-es", "publicacion-sin-opcionales",
                "publicacion-fuera-ca-rival-largo", "publicacion-copa")) {
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

        for (var campo : Datos.class.getRecordComponents()) {
            assertThat(campo.getName().toLowerCase()).doesNotContain("jugador").doesNotContain("foto");
        }

        assertThat(List.of(Formato.values())).hasSize(2);
    }

    @Test
    void comparteConElPrimerEquipoLaEleccionDelPartidoYSoloAportaSuEquipoSuRendererYSuFondo() {

        assertThat(CartelPartidoServiceBase.class).isAssignableFrom(CartelFilialServiceImpl.class);
        assertThat(CartelPartidoServiceBase.class).isAssignableFrom(CartelPrimerEquipoServiceImpl.class);
        assertThat(CartelFilialServiceImpl.EQUIPO_FILIAL).isEqualTo("Filial");
        assertThat(CartelPrimerEquipoServiceImpl.EQUIPO_PRIMER_EQUIPO).isEqualTo("Primer Equipo");
    }
}
