package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DuplicateKeyException;

import com.mikedev.mutxamelcf.dao.JuegoDao;
import com.mikedev.mutxamelcf.dao.JuegoDao.Marca;
import com.mikedev.mutxamelcf.dao.JuegoDao.Perfil;
import com.mikedev.mutxamelcf.service.JuegoConflictoException;
import com.mikedev.mutxamelcf.service.JuegoLimiteEnviosException;
import com.mikedev.mutxamelcf.service.JuegoService;
import com.mikedev.mutxamelcf.service.JuegoService.FilaRanking;
import com.mikedev.mutxamelcf.service.JuegoService.PerfilJuego;
import com.mikedev.mutxamelcf.service.JuegoService.ResultadoPuntuacion;

class JuegoServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** El código de club de la FFCV que cuenta como "nuestro" en estos tests. */
    private static final int CODIGO_PROPIO = 3010;

    // Miércoles 25/11/2026 12:00 en Madrid; la semana empieza el lunes 23/11 00:00.
    private static final LocalDateTime MIERCOLES = LocalDateTime.of(2026, 11, 25, 12, 0);
    private static final LocalDateTime LUNES = LocalDateTime.of(2026, 11, 23, 0, 0);

    private final JuegoDao dao = mock(JuegoDao.class);
    private final AliasJuego alias = new AliasJuego("");
    private JuegoServiceImpl service;

    private static Clock reloj(LocalDateTime madrid) {
        return Clock.fixed(madrid.atZone(MADRID).toInstant(), MADRID);
    }

    private JuegoServiceImpl servicio(LocalDateTime ahoraEnMadrid) {
        return new JuegoServiceImpl(dao, alias, reloj(ahoraEnMadrid), CODIGO_PROPIO, new Random(7));
    }

    private JuegoServiceImpl servicio(LocalDateTime ahoraEnMadrid, long semilla) {
        return new JuegoServiceImpl(dao, alias, reloj(ahoraEnMadrid), CODIGO_PROPIO, new Random(semilla));
    }

    @BeforeEach
    void setUp() {
        service = servicio(MIERCOLES);
    }

    private void conPerfil(String aliasPerfil, boolean oculto) {
        when(dao.obtenerPerfil(5L)).thenReturn(Optional.of(new Perfil(5, aliasPerfil, "AZUL", oculto)));
    }

    // ------------------------------------------------------------------
    // Ranking: la semana va de lunes 00:00 a lunes 00:00, en hora de Madrid
    // ------------------------------------------------------------------

    @Test
    void elRankingSemanalPideDesdeElLunesALasCeroHorasDeMadrid() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                new JuegoDao.FilaRanking(1, "Ana", 9, 30_000, MIERCOLES), new JuegoDao.FilaRanking(2, "Beto", 7, 30_000, MIERCOLES)));

        List<FilaRanking> filas = service.ranking("cabezazos", "semana");

        verify(dao).ranking("CABEZAZOS", LUNES, LUNES.plusWeeks(1), 50);
        assertThat(filas).containsExactly(new FilaRanking(1, "Ana", "PUNTOS", 9, 30_000, MIERCOLES),
                new FilaRanking(2, "Beto", "PUNTOS", 7, 30_000, MIERCOLES));
    }

    @Test
    void elDomingoALas2359SigueSiendoLaSemanaYElLunesALas0000EmpiezaOtra() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        servicio(LocalDateTime.of(2026, 11, 29, 23, 59, 59)).ranking("ATRAPA", "SEMANA");
        verify(dao).ranking("ATRAPA", LUNES, LUNES.plusWeeks(1), 50);

        servicio(LocalDateTime.of(2026, 11, 30, 0, 0, 0)).ranking("ATRAPA", "SEMANA");
        verify(dao).ranking("ATRAPA", LUNES.plusWeeks(1), LUNES.plusWeeks(2), 50);
    }

    @Test
    void elCorteEsElLunesDeMadridNoElDeUtc() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        // 23:30 UTC del domingo 29/11 son las 00:30 del lunes 30/11 en Madrid (CET, UTC+1): ya es la semana nueva.
        Clock utc = Clock.fixed(Instant.parse("2026-11-29T23:30:00Z"), MADRID);

        new JuegoServiceImpl(dao, alias, utc, CODIGO_PROPIO, new Random(7)).ranking("ATRAPA", "SEMANA");

        verify(dao).ranking("ATRAPA", LUNES.plusWeeks(1), LUNES.plusWeeks(2), 50);
    }

    @Test
    void elHistoricoNoTieneLimiteDeFechasYLaSemanaEsElPeriodoPorDefecto() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        service.ranking("ATRAPA", "HISTORICO");
        verify(dao).ranking("ATRAPA", null, null, 50);

        service.ranking("ATRAPA", null);
        verify(dao).ranking("ATRAPA", LUNES, LUNES.plusWeeks(1), 50);
    }

    @Test
    void elRankingRechazaUnJuegoOUnPeriodoQueNoExisten() {
        assertThatThrownBy(() -> service.ranking("AJEDREZ", "SEMANA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ranking(null, "SEMANA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ranking("ATRAPA", "MES")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEMANA o HISTORICO");
    }

    // ------------------------------------------------------------------
    // Perfil y equipación
    // ------------------------------------------------------------------

    @Test
    void sinPerfilElPerfilVieneVacioConEquipacionAzul() {
        when(dao.obtenerPerfil(5L)).thenReturn(Optional.empty());
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.alias()).isNull();
        assertThat(perfil.equipacion()).isEqualTo("AZUL");
        assertThat(perfil.oculto()).isFalse();
        assertThat(perfil.juegos()).containsOnlyKeys("ATRAPA", "CABEZAZOS", "PAREJAS");
        // Y salen siempre en este orden, cada uno con lo que mide.
        assertThat(perfil.juegos().keySet()).containsExactly("ATRAPA", "CABEZAZOS", "PAREJAS");
        assertThat(perfil.juegos().get("ATRAPA").marca()).isEqualTo("PUNTOS");
        assertThat(perfil.juegos().get("CABEZAZOS").marca()).isEqualTo("PUNTOS");
        assertThat(perfil.juegos().get("PAREJAS").marca()).isEqualTo("TIEMPO");
        assertThat(perfil.juegos().get("ATRAPA").semana()).isNull();
        assertThat(perfil.juegos().get("ATRAPA").historico()).isNull();
    }

    @Test
    void elPerfilDaLaMejorMarcaDeCadaPeriodoYLaPosicionAunqueEsteFueraDelTop() {
        conPerfil("Ana", false);
        Marca semana = new Marca(8, 30_000, LUNES.plusDays(1));
        Marca historico = new Marca(15, 30_000, LUNES.minusDays(30));
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(dao.mejorMarca(5L, "CABEZAZOS", LUNES, LUNES.plusWeeks(1))).thenReturn(Optional.of(semana));
        when(dao.mejorMarca(5L, "CABEZAZOS", null, null)).thenReturn(Optional.of(historico));
        when(dao.cuentasPorDelante("CABEZAZOS", LUNES, LUNES.plusWeeks(1), 5L, semana)).thenReturn(2);
        when(dao.cuentasPorDelante("CABEZAZOS", null, null, 5L, historico)).thenReturn(79); // posición 80

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.alias()).isEqualTo("Ana");
        assertThat(perfil.juegos().get("CABEZAZOS").semana().puntos()).isEqualTo(8);
        assertThat(perfil.juegos().get("CABEZAZOS").semana().posicion()).isEqualTo(3);
        assertThat(perfil.juegos().get("CABEZAZOS").historico().puntos()).isEqualTo(15);
        assertThat(perfil.juegos().get("CABEZAZOS").historico().posicion()).isEqualTo(80);
    }

    @Test
    void unPerfilOcultoNoTienePosicionPeroSiMarcas() {
        conPerfil("Ana", true);
        when(dao.mejorMarca(5L, "ATRAPA", null, null)).thenReturn(Optional.of(new Marca(40, 30_000, LUNES)));
        when(dao.mejorMarca(5L, "CABEZAZOS", null, null)).thenReturn(Optional.empty());
        when(dao.mejorMarca(eq(5L), anyString(), eq(LUNES), any())).thenReturn(Optional.empty());

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.oculto()).isTrue();
        assertThat(perfil.juegos().get("ATRAPA").historico().puntos()).isEqualTo(40);
        assertThat(perfil.juegos().get("ATRAPA").historico().posicion()).isNull();
        verify(dao, never()).cuentasPorDelante(anyString(), any(), any(), anyLong(), any());
    }

    @Test
    void laPrimeraVezQueSeEligeAliasSeCreaElPerfil() {
        when(dao.obtenerPerfil(5L)).thenReturn(Optional.empty());
        when(dao.existeAlias("MUTXI", 5L)).thenReturn(false);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.actualizarPerfil(5, "  Mútxi ", null);

        verify(dao).crearPerfil(eq(5L), eq("Mútxi"), eq("MUTXI"), eq("AZUL"), any());
        verify(dao, never()).actualizarPerfil(anyLong(), any(), any(), any(), any());
    }

    @Test
    void cambiarSoloLaEquipacionNoTocaElAlias() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.actualizarPerfil(5, null, "rosa");

        verify(dao).actualizarPerfil(eq(5L), eq(null), eq(null), eq("ROSA"), any());
        verify(dao, never()).existeAlias(anyString(), anyLong());
    }

    @Test
    void elPerfilSeCreaConLaEquipacionElegidaAunqueNoHayaAliasTodavia() {
        when(dao.obtenerPerfil(5L)).thenReturn(Optional.empty());
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.actualizarPerfil(5, null, "ROSA");

        verify(dao).crearPerfil(eq(5L), eq(null), eq(null), eq("ROSA"), any());
    }

    @Test
    void laEquipacionSoloPuedeSerAzulORosa() {
        assertThatThrownBy(() -> service.actualizarPerfil(5, null, "VERDE")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AZUL o ROSA");
        verify(dao, never()).crearPerfil(anyLong(), any(), any(), any(), any());
    }

    @Test
    void sinAliasNiEquipacionNoHayNadaQueCambiar() {
        assertThatThrownBy(() -> service.actualizarPerfil(5, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unAliasRepetidoEs409ConSuCodigo() {
        conPerfil("Ana", false);
        when(dao.existeAlias("BETO", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizarPerfil(5, "beto", null)).isInstanceOf(JuegoConflictoException.class)
                .hasMessage("Ese alias ya está en uso")
                .satisfies(e -> assertThat(((JuegoConflictoException) e).getCodigo()).isEqualTo("ALIAS_EN_USO"));
        verify(dao, never()).actualizarPerfil(anyLong(), any(), any(), any(), any());
    }

    @Test
    void dosCuentasEligiendoElMismoAliasAlaVezGanaLaPrimera() {
        conPerfil("Ana", false);
        when(dao.existeAlias("BETO", 5L)).thenReturn(false);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("UQ_JUEGO_PERFIL_ALIAS")).when(dao)
                .actualizarPerfil(anyLong(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.actualizarPerfil(5, "Beto", null)).isInstanceOf(JuegoConflictoException.class)
                .hasMessage("Ese alias ya está en uso");
    }

    @Test
    void unAliasNoValidoEs400YNoSeGuarda() {
        assertThatThrownBy(() -> service.actualizarPerfil(5, "pepe@gmail.com", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.actualizarPerfil(5, "gilipollas", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).crearPerfil(anyLong(), any(), any(), any(), any());
        verify(dao, never()).actualizarPerfil(anyLong(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Guardar puntuación
    // ------------------------------------------------------------------

    @Test
    void sinPerfilOSinAliasEs409ConCodigoAliasRequerido() {
        when(dao.obtenerPerfil(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "CABEZAZOS", 5, 20_000))
                .isInstanceOf(JuegoConflictoException.class)
                .satisfies(e -> assertThat(((JuegoConflictoException) e).getCodigo()).isEqualTo("ALIAS_REQUERIDO"))
                .hasMessageContaining("alias");

        conPerfil(null, false); // perfil con equipación pero sin alias
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "CABEZAZOS", 5, 20_000))
                .isInstanceOf(JuegoConflictoException.class);

        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @CsvSource({
            // juego, puntos, duracionMs, ¿plausible?
            "ATRAPA, 324, 45000, true", // 6/s: 270, +20 % = 324
            "ATRAPA, 325, 45000, false", //
            "ATRAPA, 21, 3000, true", // 6/s en 3 s: 18, +20 % = 21,6 → 21
            "ATRAPA, 22, 3000, false", //
            "CABEZAZOS, 54, 45000, true", // 1/s: 45, +20 % = 54
            "CABEZAZOS, 55, 45000, false", //
            "CABEZAZOS, 3, 3000, true", // 1/s en 3 s: 3, +20 % = 3,6 → 3
            "CABEZAZOS, 4, 3000, false", //
            "PAREJAS, 8, 8000, true", // las 8 parejas en 8 s: lo más rápido que vale
            "PAREJAS, 8, 7999, false", // menos de 8 s
            "PAREJAS, 8, 60000, true", //
            "PAREJAS, 7, 60000, false", // partida sin completar
            "PAREJAS, 9, 60000, false", //
            "PAREJAS, 0, 60000, false", //
            "PAREJAS, 8, 7200000, true", // 2 h
            "PAREJAS, 8, 7200001, false", //
            "ATRAPA, 999, 7200000, true", // tope de puntos
            "ATRAPA, 1000, 7200000, false", //
            "ATRAPA, -1, 20000, false", //
            "ATRAPA, 0, 20000, true", //
            "ATRAPA, 10, 2999, false", // menos de 3 s
            "ATRAPA, 10, 3000, true", //
            "ATRAPA, 10, 7200001, false", // más de 2 h
            "ATRAPA, 10, 0, false", //
    })
    void laPuntuacionTieneQueSerPlausible(String juego, int puntos, int duracionMs, boolean plausible) {
        conPerfil("Ana", false);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.empty());

        if (plausible) {
            service.guardarPuntuacion(5, juego, puntos, duracionMs);
            verify(dao).insertarPuntuacion(eq(5L), eq(juego), eq(puntos), eq(duracionMs), any());
        } else {
            assertThatThrownBy(() -> service.guardarPuntuacion(5, juego, puntos, duracionMs))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
        }
    }

    @Test
    void sinJuegoPuntosOduracionOunJuegoDesconocidoEs400() {
        conPerfil("Ana", false);

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "AJEDREZ", 5, 20_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardarPuntuacion(5, null, 5, 20_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "ATRAPA", null, 20_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "ATRAPA", 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void masDeUnEnvioCadaCincoSegundosEs429() {
        conPerfil("Ana", false);
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.of(MIERCOLES.minusSeconds(4)));

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "ATRAPA", 10, 20_000))
                .isInstanceOf(JuegoLimiteEnviosException.class);
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());

        // Justo a los 5 segundos ya vale
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.of(MIERCOLES.minusSeconds(5)));
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.guardarPuntuacion(5, "ATRAPA", 10, 20_000);

        verify(dao).insertarPuntuacion(eq(5L), eq("ATRAPA"), eq(10), eq(20_000), any());
    }

    @Test
    void masDe300EnviosAlDiaEs429YElDiaEmpiezaALasCeroHorasDeMadrid() {
        conPerfil("Ana", false);
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.empty());
        when(dao.contarPuntuacionesDesde(5L, LocalDateTime.of(2026, 11, 25, 0, 0))).thenReturn(300);

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "ATRAPA", 10, 20_000))
                .isInstanceOf(JuegoLimiteEnviosException.class).hasMessageContaining("hoy");
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());

        when(dao.contarPuntuacionesDesde(5L, LocalDateTime.of(2026, 11, 25, 0, 0))).thenReturn(299);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.guardarPuntuacion(5, "ATRAPA", 10, 20_000);

        verify(dao).insertarPuntuacion(eq(5L), eq("ATRAPA"), eq(10), eq(20_000), any());
    }

    @Test
    void laPrimeraPartidaConPuntosEsRecordDeLaSemanaYDelHistorico() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(5L, "ATRAPA", LUNES, LUNES.plusWeeks(1)))
                .thenReturn(Optional.empty(), Optional.of(new Marca(10, 30_000, MIERCOLES)));
        when(dao.mejorMarca(5L, "ATRAPA", null, null)).thenReturn(Optional.empty(), Optional.of(new Marca(10, 30_000, MIERCOLES)));
        when(dao.cuentasPorDelante(eq("ATRAPA"), any(), any(), eq(5L), any())).thenReturn(0, 4);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "atrapa", 10, 20_000);

        assertThat(r.juego()).isEqualTo("ATRAPA");
        assertThat(r.recordSemana()).isTrue();
        assertThat(r.recordHistorico()).isTrue();
        assertThat(r.posicionSemana()).isEqualTo(1);
        assertThat(r.posicionHistorico()).isEqualTo(5);
        verify(dao).insertarPuntuacion(5L, "ATRAPA", 10, 20_000, MIERCOLES);
    }

    @Test
    void esRecordSoloSiSuperaLaMejorMarcaDeCadaPeriodo() {
        conPerfil("Ana", false);
        // Mejor de la semana: 8; mejor histórica: 20. Un 12 es récord de la semana pero no histórico.
        when(dao.mejorMarca(5L, "CABEZAZOS", LUNES, LUNES.plusWeeks(1)))
                .thenReturn(Optional.of(new Marca(8, 30_000, LUNES)), Optional.of(new Marca(12, 30_000, MIERCOLES)));
        when(dao.mejorMarca(5L, "CABEZAZOS", null, null))
                .thenReturn(Optional.of(new Marca(20, 30_000, LUNES.minusDays(9))), Optional.of(new Marca(20, 30_000, LUNES.minusDays(9))));
        when(dao.cuentasPorDelante(eq("CABEZAZOS"), any(), any(), eq(5L), any())).thenReturn(2, 0);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "CABEZAZOS", 12, 40_000);

        assertThat(r.recordSemana()).isTrue();
        assertThat(r.recordHistorico()).isFalse();
        assertThat(r.posicionSemana()).isEqualTo(3);
        assertThat(r.posicionHistorico()).isEqualTo(1);
    }

    @Test
    void empatarLaMejorMarcaNoEsRecord() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(eq(5L), eq("ATRAPA"), any(), any())).thenReturn(Optional.of(new Marca(9, 30_000, LUNES)));
        when(dao.cuentasPorDelante(eq("ATRAPA"), any(), any(), eq(5L), any())).thenReturn(0);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "ATRAPA", 9, 20_000);

        assertThat(r.recordSemana()).isFalse();
        assertThat(r.recordHistorico()).isFalse();
    }

    @Test
    void unPerfilOcultoGuardaPeroNoDevuelvePosicion() {
        conPerfil("Ana", true);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty(),
                Optional.of(new Marca(5, 30_000, MIERCOLES)));

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "ATRAPA", 5, 20_000);

        assertThat(r.posicionSemana()).isNull();
        assertThat(r.posicionHistorico()).isNull();
        verify(dao).insertarPuntuacion(eq(5L), eq("ATRAPA"), eq(5), eq(20_000), any());
    }

    // ------------------------------------------------------------------
    // Moderación
    // ------------------------------------------------------------------

    private static JuegoDao.PerfilAdmin admin(long id, String aliasPerfil, boolean oculto) {
        return new JuegoDao.PerfilAdmin(id, aliasPerfil, "AZUL", oculto, "Nombre Apellido", "a@b.es", MIERCOLES, 3, 4, 6,
                7_000);
    }

    // ------------------------------------------------------------------
    // Juegos válidos
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({ "PENALTIS", "TOQUES", "REGATEA", "regatea", "AJEDREZ" })
    void losJuegosAntiguosYLosDesconocidosDan400EnTodasLasRutas(String juego) {
        conPerfil("Ana", false);

        assertThatThrownBy(() -> service.ranking(juego, "SEMANA")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El juego tiene que ser ATRAPA, CABEZAZOS o PAREJAS");
        assertThatThrownBy(() -> service.guardarPuntuacion(5, juego, 5, 20_000))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).ranking(anyString(), any(), any(), anyInt());
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @CsvSource({ "ATRAPA", "CABEZAZOS", "PAREJAS", "parejas", " Atrapa " })
    void losCuatroJuegosNuevosSonValidosSinImportarMayusculas(String juego) {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.ranking(juego, "HISTORICO")).isEmpty();
        verify(dao).ranking(juego.trim().toUpperCase(), null, null, 50);
    }

    // ------------------------------------------------------------------
    // Marca (PUNTOS o TIEMPO) y tiempo en las respuestas
    // ------------------------------------------------------------------

    @Test
    void elRankingDiceQueMideCadaJuegoYDaElTiempoDeLaPartida() {
        when(dao.ranking(eq("ATRAPA"), any(), any(), anyInt()))
                .thenReturn(List.of(new JuegoDao.FilaRanking(1, "Ana", 12, 41_000, MIERCOLES)));
        when(dao.ranking(eq("PAREJAS"), any(), any(), anyInt()))
                .thenReturn(List.of(new JuegoDao.FilaRanking(2, "Beto", 8, 31_400, MIERCOLES),
                        new JuegoDao.FilaRanking(1, "Ana", 8, 40_000, MIERCOLES)));

        assertThat(service.ranking("ATRAPA", null)).containsExactly(
                new FilaRanking(1, "Ana", "PUNTOS", 12, 41_000, MIERCOLES));
        // El orden (tiempo ascendente) lo trae el DAO; el servicio solo numera las filas.
        assertThat(service.ranking("PAREJAS", null)).containsExactly(
                new FilaRanking(1, "Beto", "TIEMPO", 8, 31_400, MIERCOLES),
                new FilaRanking(2, "Ana", "TIEMPO", 8, 40_000, MIERCOLES));
    }

    @Test
    void elPerfilDaElTiempoDeCadaMarcaYSuPosicionEnParejasEsLaDelTiempo() {
        conPerfil("Ana", false);
        Marca parejas = new Marca(8, 31_400, LUNES.plusDays(1));
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(dao.mejorMarca(5L, "PAREJAS", LUNES, LUNES.plusWeeks(1))).thenReturn(Optional.of(parejas));
        when(dao.cuentasPorDelante("PAREJAS", LUNES, LUNES.plusWeeks(1), 5L, parejas)).thenReturn(1);

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.juegos().get("PAREJAS").marca()).isEqualTo("TIEMPO");
        assertThat(perfil.juegos().get("PAREJAS").semana().puntos()).isEqualTo(8);
        assertThat(perfil.juegos().get("PAREJAS").semana().tiempoMs()).isEqualTo(31_400);
        assertThat(perfil.juegos().get("PAREJAS").semana().posicion()).isEqualTo(2);
        assertThat(perfil.juegos().get("PAREJAS").historico()).isNull();
    }

    @Test
    void elResultadoDeGuardarDiceLaMarcaYElTiempoDeLaPartida() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(dao.cuentasPorDelante(anyString(), any(), any(), anyLong(), any())).thenReturn(0);

        ResultadoPuntuacion puntos = service.guardarPuntuacion(5, "ATRAPA", 12, 41_000);
        ResultadoPuntuacion tiempo = service.guardarPuntuacion(5, "PAREJAS", 8, 31_400);

        assertThat(puntos.marca()).isEqualTo("PUNTOS");
        assertThat(puntos.tiempoMs()).isEqualTo(41_000);
        assertThat(puntos.puntos()).isEqualTo(12);
        assertThat(tiempo.marca()).isEqualTo("TIEMPO");
        assertThat(tiempo.tiempoMs()).isEqualTo(31_400);
        assertThat(tiempo.puntos()).isEqualTo(8);
    }

    // ------------------------------------------------------------------
    // PAREJAS: el récord es el menor tiempo
    // ------------------------------------------------------------------

    @Test
    void enParejasEsRecordSoloSiTardaMenosQueLaMejorAnterior() {
        conPerfil("Ana", false);
        // Mejor de la semana: 40 s; histórica: 30 s. Con 35 s es récord de la semana pero no histórico.
        when(dao.mejorMarca(5L, "PAREJAS", LUNES, LUNES.plusWeeks(1)))
                .thenReturn(Optional.of(new Marca(8, 40_000, LUNES)), Optional.of(new Marca(8, 35_000, MIERCOLES)));
        when(dao.mejorMarca(5L, "PAREJAS", null, null))
                .thenReturn(Optional.of(new Marca(8, 30_000, LUNES.minusDays(9))),
                        Optional.of(new Marca(8, 30_000, LUNES.minusDays(9))));
        when(dao.cuentasPorDelante(eq("PAREJAS"), any(), any(), eq(5L), any())).thenReturn(1, 0);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "PAREJAS", 8, 35_000);

        assertThat(r.recordSemana()).isTrue();
        assertThat(r.recordHistorico()).isFalse();
        assertThat(r.posicionSemana()).isEqualTo(2);
        assertThat(r.posicionHistorico()).isEqualTo(1);
    }

    @Test
    void enParejasEmpatarOEmpeorarElTiempoNoEsRecordYLaPrimeraPartidaSi() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(eq(5L), eq("PAREJAS"), any(), any())).thenReturn(Optional.of(new Marca(8, 30_000, LUNES)));
        when(dao.cuentasPorDelante(eq("PAREJAS"), any(), any(), eq(5L), any())).thenReturn(0);

        assertThat(service.guardarPuntuacion(5, "PAREJAS", 8, 30_000).recordSemana()).isFalse(); // empata
        assertThat(service.guardarPuntuacion(5, "PAREJAS", 8, 55_000).recordHistorico()).isFalse(); // peor

        when(dao.mejorMarca(eq(5L), eq("PAREJAS"), any(), any())).thenReturn(Optional.empty());

        ResultadoPuntuacion primera = service.guardarPuntuacion(5, "PAREJAS", 8, 90_000);

        assertThat(primera.recordSemana()).isTrue();
        assertThat(primera.recordHistorico()).isTrue();
    }

    @Test
    void enParejasSoloSeGuardanLasPartidasCompletas() {
        conPerfil("Ana", false);

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "PAREJAS", 5, 60_000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("completas");
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    // ------------------------------------------------------------------
    // Escudos para Parejas
    // ------------------------------------------------------------------

    private static JuegoDao.ClubConEscudo club(long id, String nombre, Integer codigo) {
        return new JuegoDao.ClubConEscudo(id, nombre, codigo, 3);
    }

    private static List<JuegoDao.ClubConEscudo> clubes(int cuantos) {
        java.util.ArrayList<JuegoDao.ClubConEscudo> lista = new java.util.ArrayList<>();

        for (int i = 1; i <= cuantos; i++) {
            lista.add(club(i, "Club " + i, 100 + i));
        }

        return lista;
    }

    @Test
    void sinCantidadSeDan8EscudosConLaMismaUrlYVersionQueElRestoDeEscudos() {
        when(dao.clubesConEscudo()).thenReturn(clubes(20));
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of());

        List<JuegoService.EscudoJuego> escudos = service.escudos(null);

        assertThat(escudos).hasSize(8);
        assertThat(escudos).extracting(JuegoService.EscudoJuego::id).doesNotHaveDuplicates();
        assertThat(escudos).allSatisfy(e -> {
            assertThat(e.escudoUrl()).isEqualTo("/api/public/escudos-rivales/" + e.id() + "?v=3");
            assertThat(e.nombre()).isEqualTo("Club " + e.id());
        });
    }

    @ParameterizedTest
    @CsvSource({ "2, 2", "5, 5", "12, 12" })
    void lacantidadSePuedePedirDe2a12(int pedidos, int esperados) {
        when(dao.clubesConEscudo()).thenReturn(clubes(30));
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of());

        assertThat(service.escudos(pedidos)).hasSize(esperados);
    }

    @ParameterizedTest
    @CsvSource({ "1", "13", "0", "-3", "100" })
    void unaCantidadFueraDe2a12Es400(int pedidos) {
        assertThatThrownBy(() -> service.escudos(pedidos)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 2 y 12");
        verify(dao, never()).clubesConEscudo();
    }

    @Test
    void nuncaSaleNuestroClubNiPorCodigoNiPorNombre() {
        java.util.ArrayList<JuegoDao.ClubConEscudo> lista = new java.util.ArrayList<>(clubes(10));
        lista.add(club(50, "Otro nombre", CODIGO_PROPIO)); // nuestro código de la FFCV
        lista.add(club(51, "C.F. Mutxamel", null)); // nuestro nombre
        lista.add(club(52, "Mutxamel CF B", 999)); // un equipo nuestro

        when(dao.clubesConEscudo()).thenReturn(lista);
        // Aunque salgan en las clasificaciones, no se eligen.
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of(50L, 51L, 52L, 1L));

        for (int vez = 0; vez < 25; vez++) {
            assertThat(servicio(MIERCOLES, vez).escudos(12)).extracting(JuegoService.EscudoJuego::id)
                    .doesNotContain(50L, 51L, 52L).hasSize(10);
        }
    }

    @Test
    void primeroLosClubesDeLasClasificacionesDeLaTemporadaYLuegoSeCompletaConOtros() {
        when(dao.clubesConEscudo()).thenReturn(clubes(20));
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of(3L, 4L, 5L, 6L, 7L));

        for (int vez = 0; vez < 25; vez++) {
            // 3 de 5 actuales: salen 3 de esos 5 y nada más
            assertThat(servicio(MIERCOLES, vez).escudos(3)).extracting(JuegoService.EscudoJuego::id)
                    .isSubsetOf(3L, 4L, 5L, 6L, 7L);

            // 8 pedidos: los 5 actuales sí o sí, más 3 de los demás
            List<Long> ocho = servicio(MIERCOLES, vez).escudos(8).stream().map(JuegoService.EscudoJuego::id).toList();

            assertThat(ocho).contains(3L, 4L, 5L, 6L, 7L).hasSize(8).doesNotHaveDuplicates();
        }
    }

    @Test
    void conMenosClubesConEscudoQueLosPedidosDevuelveLosQueHay() {
        when(dao.clubesConEscudo()).thenReturn(clubes(3));
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of());

        assertThat(service.escudos(8)).hasSize(3);

        when(dao.clubesConEscudo()).thenReturn(List.of());

        assertThat(service.escudos(8)).isEmpty();
    }

    @Test
    void losEscudosSeEligenAlAzar() {
        when(dao.clubesConEscudo()).thenReturn(clubes(30));
        when(dao.clubesDeLasClasificacionesActuales()).thenReturn(java.util.Set.of());

        java.util.Set<List<Long>> distintas = new java.util.HashSet<>();

        for (int vez = 0; vez < 10; vez++) {
            distintas.add(servicio(MIERCOLES, vez).escudos(8).stream().map(JuegoService.EscudoJuego::id).toList());
        }

        assertThat(distintas.size()).isGreaterThan(1);
    }

    @Test
    void elListadoFiltraPorAliasNormalizado() {
        when(dao.listarPerfilesAdmin(anyString(), anyInt())).thenReturn(List.of(admin(1, "Mútxi", false)));

        assertThat(service.listarPerfilesAdmin(" mutxi ")).hasSize(1);
        verify(dao).listarPerfilesAdmin("MUTXI", 500);

        service.listarPerfilesAdmin(null);
        verify(dao).listarPerfilesAdmin("", 500);
    }

    @Test
    void elAdministradorCambiaElAliasConLasMismasReglas() {
        when(dao.obtenerPerfilAdmin(7)).thenReturn(Optional.of(admin(7, "Viejo", false)));
        when(dao.existeAlias("NUEVO", 7)).thenReturn(false);

        service.cambiarAliasAdmin(7, "Nuevo");

        verify(dao).actualizarPerfil(eq(7L), eq("Nuevo"), eq("NUEVO"), eq(null), any());

        when(dao.existeAlias("OCUPADO", 7)).thenReturn(true);
        assertThatThrownBy(() -> service.cambiarAliasAdmin(7, "Ocupado")).isInstanceOf(JuegoConflictoException.class);
        assertThatThrownBy(() -> service.cambiarAliasAdmin(7, "puta")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ocultarYMostrarCambianElEstadoDelPerfil() {
        when(dao.obtenerPerfilAdmin(7)).thenReturn(Optional.of(admin(7, "Ana", false)));

        service.ocultarAdmin(7, true);
        verify(dao).actualizarOculto(eq(7L), eq(true), any());

        service.ocultarAdmin(7, false);
        verify(dao).actualizarOculto(eq(7L), eq(false), any());
    }

    @Test
    void borrarUnaPuntuacionOTodasLasDeUnPerfil() {
        when(dao.obtenerPerfilAdmin(7)).thenReturn(Optional.of(admin(7, "Ana", false)));
        when(dao.borrarPuntuacion(11)).thenReturn(1);
        when(dao.borrarPuntuacion(12)).thenReturn(0);
        when(dao.borrarPuntuacionesDeUsuario(7)).thenReturn(3);

        service.borrarPuntuacionAdmin(11);
        assertThatThrownBy(() -> service.borrarPuntuacionAdmin(12)).isInstanceOf(NoSuchElementException.class);
        assertThat(service.borrarPuntuacionesAdmin(7)).isEqualTo(3);
    }

    @Test
    void unPerfilQueNoExisteEs404EnTodaLaModeracion() {
        when(dao.obtenerPerfilAdmin(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarAliasAdmin(99, "Nuevo")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.ocultarAdmin(99, true)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.borrarPuntuacionesAdmin(99)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.puntuacionesAdmin(99)).isInstanceOf(NoSuchElementException.class);
        verify(dao, never()).actualizarOculto(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void elLunesDeLaSemanaSeCalculaBienEnLosBordes() {
        assertThat(JuegoServiceImpl.inicioDeSemana(LocalDateTime.of(2026, 11, 23, 0, 0))).isEqualTo(LUNES);
        assertThat(JuegoServiceImpl.inicioDeSemana(LocalDateTime.of(2026, 11, 29, 23, 59, 59))).isEqualTo(LUNES);
        assertThat(JuegoServiceImpl.inicioDeSemana(LocalDateTime.of(2026, 11, 30, 0, 0))).isEqualTo(LUNES.plusWeeks(1));
    }
}
