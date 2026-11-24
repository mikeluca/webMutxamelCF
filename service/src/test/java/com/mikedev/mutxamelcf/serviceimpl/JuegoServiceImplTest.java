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
import com.mikedev.mutxamelcf.service.JuegoService.FilaRanking;
import com.mikedev.mutxamelcf.service.JuegoService.PerfilJuego;
import com.mikedev.mutxamelcf.service.JuegoService.ResultadoPuntuacion;

class JuegoServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

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
        return new JuegoServiceImpl(dao, alias, reloj(ahoraEnMadrid));
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
                new JuegoDao.FilaRanking(1, "Ana", 9, MIERCOLES), new JuegoDao.FilaRanking(2, "Beto", 7, MIERCOLES)));

        List<FilaRanking> filas = service.ranking("penaltis", "semana");

        verify(dao).ranking("PENALTIS", LUNES, LUNES.plusWeeks(1), 50);
        assertThat(filas).containsExactly(new FilaRanking(1, "Ana", 9, MIERCOLES),
                new FilaRanking(2, "Beto", 7, MIERCOLES));
    }

    @Test
    void elDomingoALas2359SigueSiendoLaSemanaYElLunesALas0000EmpiezaOtra() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        servicio(LocalDateTime.of(2026, 11, 29, 23, 59, 59)).ranking("TOQUES", "SEMANA");
        verify(dao).ranking("TOQUES", LUNES, LUNES.plusWeeks(1), 50);

        servicio(LocalDateTime.of(2026, 11, 30, 0, 0, 0)).ranking("TOQUES", "SEMANA");
        verify(dao).ranking("TOQUES", LUNES.plusWeeks(1), LUNES.plusWeeks(2), 50);
    }

    @Test
    void elCorteEsElLunesDeMadridNoElDeUtc() {
        when(dao.ranking(anyString(), any(), any(), anyInt())).thenReturn(List.of());

        // 23:30 UTC del domingo 29/11 son las 00:30 del lunes 30/11 en Madrid (CET, UTC+1): ya es la semana nueva.
        Clock utc = Clock.fixed(Instant.parse("2026-11-29T23:30:00Z"), MADRID);

        new JuegoServiceImpl(dao, alias, utc).ranking("ATRAPA", "SEMANA");

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
        assertThatThrownBy(() -> service.ranking("TOQUES", "MES")).isInstanceOf(IllegalArgumentException.class)
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
        assertThat(perfil.juegos()).containsOnlyKeys("PENALTIS", "TOQUES", "ATRAPA");
        assertThat(perfil.juegos().get("TOQUES").semana()).isNull();
        assertThat(perfil.juegos().get("TOQUES").historico()).isNull();
    }

    @Test
    void elPerfilDaLaMejorMarcaDeCadaPeriodoYLaPosicionAunqueEsteFueraDelTop() {
        conPerfil("Ana", false);
        Marca semana = new Marca(8, LUNES.plusDays(1));
        Marca historico = new Marca(15, LUNES.minusDays(30));
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(dao.mejorMarca(5L, "PENALTIS", LUNES, LUNES.plusWeeks(1))).thenReturn(Optional.of(semana));
        when(dao.mejorMarca(5L, "PENALTIS", null, null)).thenReturn(Optional.of(historico));
        when(dao.cuentasPorDelante("PENALTIS", LUNES, LUNES.plusWeeks(1), 5L, semana)).thenReturn(2);
        when(dao.cuentasPorDelante("PENALTIS", null, null, 5L, historico)).thenReturn(79); // posición 80

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.alias()).isEqualTo("Ana");
        assertThat(perfil.juegos().get("PENALTIS").semana().puntos()).isEqualTo(8);
        assertThat(perfil.juegos().get("PENALTIS").semana().posicion()).isEqualTo(3);
        assertThat(perfil.juegos().get("PENALTIS").historico().puntos()).isEqualTo(15);
        assertThat(perfil.juegos().get("PENALTIS").historico().posicion()).isEqualTo(80);
    }

    @Test
    void unPerfilOcultoNoTienePosicionPeroSiMarcas() {
        conPerfil("Ana", true);
        when(dao.mejorMarca(5L, "TOQUES", null, null)).thenReturn(Optional.of(new Marca(40, LUNES)));
        when(dao.mejorMarca(5L, "PENALTIS", null, null)).thenReturn(Optional.empty());
        when(dao.mejorMarca(5L, "ATRAPA", null, null)).thenReturn(Optional.empty());
        when(dao.mejorMarca(eq(5L), anyString(), eq(LUNES), any())).thenReturn(Optional.empty());

        PerfilJuego perfil = service.perfil(5);

        assertThat(perfil.oculto()).isTrue();
        assertThat(perfil.juegos().get("TOQUES").historico().puntos()).isEqualTo(40);
        assertThat(perfil.juegos().get("TOQUES").historico().posicion()).isNull();
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

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "PENALTIS", 5, 20_000))
                .isInstanceOf(JuegoConflictoException.class)
                .satisfies(e -> assertThat(((JuegoConflictoException) e).getCodigo()).isEqualTo("ALIAS_REQUERIDO"))
                .hasMessageContaining("alias");

        conPerfil(null, false); // perfil con equipación pero sin alias
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "PENALTIS", 5, 20_000))
                .isInstanceOf(JuegoConflictoException.class);

        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @CsvSource({
            // juego, puntos, duracionMs, ¿plausible?
            "PENALTIS, 2, 3000, true", // 1 cada 1,5 s + 20 %: 2,4 → 2
            "PENALTIS, 3, 3000, false", //
            "PENALTIS, 36, 45000, true", // 45 s / 1,5 s = 30, +20 % = 36
            "PENALTIS, 37, 45000, false", //
            "TOQUES, 270, 45000, true", // 5/s: 225, +20 % = 270
            "TOQUES, 271, 45000, false", //
            "ATRAPA, 162, 45000, true", // 3/s: 135, +20 % = 162
            "ATRAPA, 163, 45000, false", //
            "TOQUES, 999, 7200000, true", // tope de puntos
            "TOQUES, 1000, 7200000, false", //
            "TOQUES, -1, 20000, false", //
            "TOQUES, 0, 20000, true", //
            "TOQUES, 10, 2999, false", // menos de 3 s
            "TOQUES, 10, 3000, true", //
            "TOQUES, 10, 7200001, false", // más de 2 h
            "TOQUES, 10, 0, false", //
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
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "TOQUES", null, 20_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardarPuntuacion(5, "TOQUES", 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void masDeUnEnvioCadaCincoSegundosEs429() {
        conPerfil("Ana", false);
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.of(MIERCOLES.minusSeconds(4)));

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "TOQUES", 10, 20_000))
                .isInstanceOf(JuegoLimiteEnviosException.class);
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());

        // Justo a los 5 segundos ya vale
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.of(MIERCOLES.minusSeconds(5)));
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.guardarPuntuacion(5, "TOQUES", 10, 20_000);

        verify(dao).insertarPuntuacion(eq(5L), eq("TOQUES"), eq(10), eq(20_000), any());
    }

    @Test
    void masDe300EnviosAlDiaEs429YElDiaEmpiezaALasCeroHorasDeMadrid() {
        conPerfil("Ana", false);
        when(dao.ultimaPuntuacion(5L)).thenReturn(Optional.empty());
        when(dao.contarPuntuacionesDesde(5L, LocalDateTime.of(2026, 11, 25, 0, 0))).thenReturn(300);

        assertThatThrownBy(() -> service.guardarPuntuacion(5, "TOQUES", 10, 20_000))
                .isInstanceOf(JuegoLimiteEnviosException.class).hasMessageContaining("hoy");
        verify(dao, never()).insertarPuntuacion(anyLong(), anyString(), anyInt(), anyInt(), any());

        when(dao.contarPuntuacionesDesde(5L, LocalDateTime.of(2026, 11, 25, 0, 0))).thenReturn(299);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());

        service.guardarPuntuacion(5, "TOQUES", 10, 20_000);

        verify(dao).insertarPuntuacion(eq(5L), eq("TOQUES"), eq(10), eq(20_000), any());
    }

    @Test
    void laPrimeraPartidaConPuntosEsRecordDeLaSemanaYDelHistorico() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(5L, "TOQUES", LUNES, LUNES.plusWeeks(1)))
                .thenReturn(Optional.empty(), Optional.of(new Marca(10, MIERCOLES)));
        when(dao.mejorMarca(5L, "TOQUES", null, null)).thenReturn(Optional.empty(), Optional.of(new Marca(10, MIERCOLES)));
        when(dao.cuentasPorDelante(eq("TOQUES"), any(), any(), eq(5L), any())).thenReturn(0, 4);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "toques", 10, 20_000);

        assertThat(r.juego()).isEqualTo("TOQUES");
        assertThat(r.recordSemana()).isTrue();
        assertThat(r.recordHistorico()).isTrue();
        assertThat(r.posicionSemana()).isEqualTo(1);
        assertThat(r.posicionHistorico()).isEqualTo(5);
        verify(dao).insertarPuntuacion(5L, "TOQUES", 10, 20_000, MIERCOLES);
    }

    @Test
    void esRecordSoloSiSuperaLaMejorMarcaDeCadaPeriodo() {
        conPerfil("Ana", false);
        // Mejor de la semana: 8; mejor histórica: 20. Un 12 es récord de la semana pero no histórico.
        when(dao.mejorMarca(5L, "PENALTIS", LUNES, LUNES.plusWeeks(1)))
                .thenReturn(Optional.of(new Marca(8, LUNES)), Optional.of(new Marca(12, MIERCOLES)));
        when(dao.mejorMarca(5L, "PENALTIS", null, null))
                .thenReturn(Optional.of(new Marca(20, LUNES.minusDays(9))), Optional.of(new Marca(20, LUNES.minusDays(9))));
        when(dao.cuentasPorDelante(eq("PENALTIS"), any(), any(), eq(5L), any())).thenReturn(2, 0);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "PENALTIS", 12, 40_000);

        assertThat(r.recordSemana()).isTrue();
        assertThat(r.recordHistorico()).isFalse();
        assertThat(r.posicionSemana()).isEqualTo(3);
        assertThat(r.posicionHistorico()).isEqualTo(1);
    }

    @Test
    void empatarLaMejorMarcaNoEsRecord() {
        conPerfil("Ana", false);
        when(dao.mejorMarca(eq(5L), eq("ATRAPA"), any(), any())).thenReturn(Optional.of(new Marca(9, LUNES)));
        when(dao.cuentasPorDelante(eq("ATRAPA"), any(), any(), eq(5L), any())).thenReturn(0);

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "ATRAPA", 9, 20_000);

        assertThat(r.recordSemana()).isFalse();
        assertThat(r.recordHistorico()).isFalse();
    }

    @Test
    void unPerfilOcultoGuardaPeroNoDevuelvePosicion() {
        conPerfil("Ana", true);
        when(dao.mejorMarca(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty(),
                Optional.of(new Marca(5, MIERCOLES)));

        ResultadoPuntuacion r = service.guardarPuntuacion(5, "TOQUES", 5, 20_000);

        assertThat(r.posicionSemana()).isNull();
        assertThat(r.posicionHistorico()).isNull();
        verify(dao).insertarPuntuacion(eq(5L), eq("TOQUES"), eq(5), eq(20_000), any());
    }

    // ------------------------------------------------------------------
    // Moderación
    // ------------------------------------------------------------------

    private static JuegoDao.PerfilAdmin admin(long id, String aliasPerfil, boolean oculto) {
        return new JuegoDao.PerfilAdmin(id, aliasPerfil, "AZUL", oculto, "Nombre Apellido", "a@b.es", MIERCOLES, 3, 4, 5,
                6);
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
