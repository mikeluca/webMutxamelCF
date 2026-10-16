package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.AsignacionEquipoFfcv;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.DescansoEquipo;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.EstadoFilaFfcv;
import com.mikedev.mutxamelcf.model.FfcvFilaPrevisualizacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;

class ImportacionFfcvServiceImplTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    private static final String PRIMERA = "Primera FFCV, Grup 4";
    private static final String SEGONA = "Segona FFCV, Grup 7";
    private static final String JUVENIL = "Segona FFCV Juvenil, Grup 7";
    private static final String CADETE = "Primera Cadet, Grup 10";

    private static final long PRIMER_EQUIPO = 1L;
    private static final long FILIAL = 2L;
    private static final long JUVENIL_A = 3L;
    private static final long ESCUELITA = 4L;
    private static final long CADETE_A = 5L;

    private PartidoDao partidoDao;
    private EquipoFfcvDao equipoFfcvDao;
    private DescansoEquipoDao descansoEquipoDao;
    private EquipoDao equipoDao;
    private ConvocatoriaDao convocatoriaDao;

    private ImportacionFfcvServiceImpl service;

    private final List<EquipoFfcv> relaciones = new ArrayList<>();
    private final List<Partido> partidos = new ArrayList<>();
    private final List<DescansoEquipo> descansos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        partidoDao = mock(PartidoDao.class);
        equipoFfcvDao = mock(EquipoFfcvDao.class);
        descansoEquipoDao = mock(DescansoEquipoDao.class);
        equipoDao = mock(EquipoDao.class);
        convocatoriaDao = mock(ConvocatoriaDao.class);

        service = new ImportacionFfcvServiceImpl(
                partidoDao, equipoFfcvDao, descansoEquipoDao, equipoDao, convocatoriaDao);

        List<Equipo> equipos = List.of(
                equipo(PRIMER_EQUIPO, "Primer Equipo"),
                equipo(FILIAL, "Filial"),
                equipo(JUVENIL_A, "Juvenil A"),
                equipo(ESCUELITA, "Escuelita"),
                equipo(CADETE_A, "Cadete A"));

        when(equipoDao.obtenerTodos()).thenReturn(equipos);
        for (Equipo equipo : equipos) {
            when(equipoDao.obtenerEquipoPorId(equipo.getId())).thenReturn(equipo);
        }

        relaciones.add(new EquipoFfcv(10L, PRIMER_EQUIPO, PRIMERA, null));
        relaciones.add(new EquipoFfcv(11L, FILIAL, SEGONA, "B"));
        relaciones.add(new EquipoFfcv(12L, JUVENIL_A, JUVENIL, "A"));

        when(equipoFfcvDao.listarTodos()).thenAnswer(inv -> new ArrayList<>(relaciones));
        when(equipoFfcvDao.obtenerPorCompeticionYLetra(any(), any())).thenAnswer(inv -> relaciones.stream()
                .filter(r -> r.getCompeticion().equals(inv.getArgument(0))
                        && java.util.Objects.equals(r.getLetra(), inv.getArgument(1)))
                .findFirst().orElse(null));
        when(equipoFfcvDao.crear(any())).thenAnswer(inv -> {
            EquipoFfcv nueva = inv.getArgument(0);
            nueva.setId(100L + relaciones.size());
            relaciones.add(nueva);
            return nueva;
        });

        when(partidoDao.obtenerPorRangoFechas(any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(0);
            LocalDate hasta = inv.getArgument(1);
            return partidos.stream()
                    .filter(p -> {
                        LocalDate dia = ((java.sql.Date) p.getDia()).toLocalDate();
                        return !dia.isBefore(desde) && !dia.isAfter(hasta);
                    })
                    .toList();
        });

        when(descansoEquipoDao.obtenerPorRangoFechas(any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(0);
            LocalDate hasta = inv.getArgument(1);
            return descansos.stream()
                    .filter(d -> !d.getFecha().isBefore(desde) && !d.getFecha().isAfter(hasta))
                    .toList();
        });
    }

    // ---- utilidades ------------------------------------------------------

    private static Equipo equipo(long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

    private static Partido partido(long id, long equipoId, LocalDate dia, String origen,
            String rival, String hora, String campo, Integer jornada) {
        Partido partido = new Partido();
        partido.setId(id);
        partido.setEquipoId(equipoId);
        partido.setDia(java.sql.Date.valueOf(dia));
        partido.setOrigen(origen);
        partido.setRival(rival);
        partido.setHora(hora);
        partido.setCampo(campo);
        partido.setJornada(jornada);
        partido.setResultado("2-1");
        return partido;
    }

    private static final String CABECERA = ";Campo;Dirección Campo;Hora;Jornada;Árbitro;Resul.;Observaciones";

    private static String partidoCsv(String ladoMutxamel, boolean local, String rival, String campo, String hora,
            int jornada) {
        String equipos = local ? ladoMutxamel + "  -  " + rival : rival + "  -  " + ladoMutxamel;
        return equipos + ";" + campo + ";Calle Cualquiera;" + hora + " ;" + jornada + ";Árbitro Ejemplo  ; - ;";
    }

    private static byte[] csv(String... lineas) {
        return String.join("\r\n", lineas).getBytes(Charset.forName("windows-1252"));
    }

    private static byte[] archivoBasico() {
        return csv(
                "Listado de Partidos",
                " ",
                "  17-10-2026, (Sábado)",
                " Segona FFCV\t, Grup - 7" + CABECERA,
                partidoCsv("Mutxamel C.F. \"B\"", true, "C.F. Calpe", "Camp Nou", "19:30", 4),
                " ",
                "  18-10-2026, (Domingo)",
                " Primera FFCV, Grup - 4" + CABECERA,
                partidoCsv("Mutxamel C.F.", false, "Pego C.F.", "Cervantes", "17:00", 6));
    }

    private FfcvFilaPrevisualizacion fila(PrevisualizacionFfcv previsualizacion, String competicion, String letra) {
        return previsualizacion.filas().stream()
                .filter(f -> f.competicion().equals(competicion) && java.util.Objects.equals(f.letra(), letra))
                .findFirst().orElseThrow();
    }

    // ---- previsualizar: estados -------------------------------------------

    @Test
    void filaSinPartidoExistenteEsNuevaYConoceSuEquipo() {
        PrevisualizacionFfcv resultado = service.previsualizar(archivoBasico());

        assertThat(resultado.desde()).isEqualTo(SABADO);
        assertThat(resultado.hasta()).isEqualTo(DOMINGO);
        assertThat(resultado.errores()).isEmpty();

        FfcvFilaPrevisualizacion pego = fila(resultado, PRIMERA, null);
        assertThat(pego.estado()).isEqualTo(EstadoFilaFfcv.NUEVO);
        assertThat(pego.equipoId()).isEqualTo(PRIMER_EQUIPO);
        assertThat(pego.equipoNombre()).isEqualTo("Primer Equipo");
        assertThat(pego.local()).isFalse();
        assertThat(pego.rival()).isEqualTo("Pego C.F.");
    }

    @Test
    void partidoFfcvConOtraHoraEsActualizadoYDescribeElCambio() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "C.F. Calpe", "18:00", "Camp Nou", 4));

        PrevisualizacionFfcv resultado = service.previsualizar(archivoBasico());

        FfcvFilaPrevisualizacion filial = fila(resultado, SEGONA, "B");
        assertThat(filial.estado()).isEqualTo(EstadoFilaFfcv.ACTUALIZADO);
        assertThat(filial.partidoId()).isEqualTo(50L);
        assertThat(filial.cambios()).containsExactly("Hora: 18:00 → 19:30");
    }

    @Test
    void cambiaElCampoOElRivalTambienEsActualizado() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "Otro rival", "19:30", "Otro campo", 4));

        FfcvFilaPrevisualizacion filial = fila(service.previsualizar(archivoBasico()), SEGONA, "B");

        assertThat(filial.estado()).isEqualTo(EstadoFilaFfcv.ACTUALIZADO);
        assertThat(filial.cambios()).hasSize(2);
    }

    @Test
    void partidoFfcvIgualEsSinCambios() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "C.F. Calpe", "19:30", "Camp Nou", 4));

        FfcvFilaPrevisualizacion filial = fila(service.previsualizar(archivoBasico()), SEGONA, "B");

        assertThat(filial.estado()).isEqualTo(EstadoFilaFfcv.SIN_CAMBIOS);
        assertThat(filial.cambios()).isEmpty();
    }

    @Test
    void competicionSinRelacionEsSinEquipo() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                partidoCsv("Mutxamel C.F. \"A\"", true, "Rival", "Campo Municipal", "10:00", 3));

        PrevisualizacionFfcv resultado = service.previsualizar(archivo);

        FfcvFilaPrevisualizacion cadete = fila(resultado, CADETE, "A");
        assertThat(cadete.estado()).isEqualTo(EstadoFilaFfcv.SIN_EQUIPO);
        assertThat(cadete.equipoId()).isNull();
    }

    @Test
    void mismaCompeticionConOtraLetraSinRelacionEsSinEquipo() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Segona FFCV, Grup - 7" + CABECERA,
                partidoCsv("Mutxamel C.F. \"C\"", true, "Rival", "Campo Municipal", "10:00", 3));

        assertThat(service.previsualizar(archivo).filas())
                .singleElement()
                .satisfies(f -> assertThat(f.estado()).isEqualTo(EstadoFilaFfcv.SIN_EQUIPO));
    }

    @Test
    void siYaHayUnPartidoManualEseDiaEsConflictoManual() {
        partidos.add(partido(60L, FILIAL, SABADO, "MANUAL", "Amistoso CF", "12:00", "Otro", null));

        FfcvFilaPrevisualizacion filial = fila(service.previsualizar(archivoBasico()), SEGONA, "B");

        assertThat(filial.estado()).isEqualTo(EstadoFilaFfcv.CONFLICTO_MANUAL);
        assertThat(filial.partidoId()).isEqualTo(60L);
    }

    @Test
    void elPartidoManualDeOtroDiaNoEsConflicto() {
        partidos.add(partido(60L, FILIAL, DOMINGO, "MANUAL", "Amistoso CF", "12:00", "Otro", null));

        assertThat(fila(service.previsualizar(archivoBasico()), SEGONA, "B").estado())
                .isEqualTo(EstadoFilaFfcv.NUEVO);
    }

    // ---- descansos ----------------------------------------------------------

    @Test
    void descansanLosEquiposConRelacionQueNoAparecenYNuncaLosQueNoTienenRelacion() {
        // El archivo solo trae a Filial y al Primer Equipo; Juvenil A tiene relación y no aparece.
        PrevisualizacionFfcv resultado = service.previsualizar(archivoBasico());

        assertThat(resultado.descansan()).extracting(d -> d.equipoNombre()).containsExactly("Juvenil A");
        assertThat(resultado.descansan()).extracting(d -> d.equipoId()).doesNotContain(ESCUELITA);
    }

    @Test
    void unEquipoConFilaSinEquipoNoEsElDeOtraRelacionNiDescansa() {
        PrevisualizacionFfcv resultado = service.previsualizar(archivoBasico());

        assertThat(resultado.descansan()).extracting(d -> d.equipoNombre()).doesNotContain("Escuelita", "Cadete A");
    }

    // ---- desaparecidos --------------------------------------------------------

    @Test
    void partidoFfcvDelRangoQueYaNoVieneSeListaComoDesaparecido() {
        partidos.add(partido(70L, JUVENIL_A, SABADO, "FFCV", "Rival Juvenil", "16:00", "Campo", 4));
        when(convocatoriaDao.obtenerPorPartidoId(70L)).thenReturn(new Convocatoria());

        PrevisualizacionFfcv resultado = service.previsualizar(archivoBasico());

        assertThat(resultado.desaparecidos()).singleElement().satisfies(d -> {
            assertThat(d.partidoId()).isEqualTo(70L);
            assertThat(d.equipoNombre()).isEqualTo("Juvenil A");
            assertThat(d.fecha()).isEqualTo(SABADO);
            assertThat(d.tieneConvocatoria()).isTrue();
        });
    }

    @Test
    void losPartidosManualesYLosDeFueraDelRangoNoSonDesaparecidos() {
        partidos.add(partido(71L, JUVENIL_A, SABADO, "MANUAL", "Amistoso", "16:00", "Campo", null));
        partidos.add(partido(72L, JUVENIL_A, SABADO.plusDays(14), "FFCV", "Dentro de dos semanas", "16:00", "C", 6));

        assertThat(service.previsualizar(archivoBasico()).desaparecidos()).isEmpty();
    }

    // ---- errores del archivo ----------------------------------------------------

    @Test
    void archivoVacioONoReconocibleSeRechaza() {
        assertThatThrownBy(() -> service.previsualizar(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.previsualizar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.previsualizar(" \r\n \r\n".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laFilaQueNoEsDelMutxamelSeDevuelveComoErrorSinPararElResto() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Segona FFCV, Grup - 7" + CABECERA,
                partidoCsv("Mutxamel C.F. \"B\"", true, "C.F. Calpe", "Camp Nou", "19:30", 4),
                "Uno C.F.  -  Otro C.F.;Campo Municipal;Dir;10:00 ;1;Árbitro Ejemplo  ; - ;");

        PrevisualizacionFfcv resultado = service.previsualizar(archivo);

        assertThat(resultado.filas()).hasSize(1);
        assertThat(resultado.errores()).hasSize(1);
        assertThat(resultado.errores().get(0).linea()).isEqualTo(4);
    }

    @Test
    void dosFilasDelMismoEquipoYDiaSeReportanYSoloSeUsaLaPrimera() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Segona FFCV, Grup - 7" + CABECERA,
                partidoCsv("Mutxamel C.F. \"B\"", true, "C.F. Calpe", "Camp Nou", "19:30", 4),
                partidoCsv("Mutxamel C.F. \"B\"", true, "Otro CF", "Otro campo", "21:00", 5));

        PrevisualizacionFfcv resultado = service.previsualizar(archivo);

        assertThat(resultado.filas()).singleElement().satisfies(f -> assertThat(f.rival()).isEqualTo("C.F. Calpe"));
        assertThat(resultado.errores()).hasSize(1);
    }

    @Test
    void previsualizarNoEscribeNada() {
        service.previsualizar(archivoBasico());

        verify(partidoDao, never()).crear(any());
        verify(partidoDao, never()).actualizarDatosFfcv(any());
        verify(equipoFfcvDao, never()).crear(any());
        verify(descansoEquipoDao, never()).crear(any());
        verify(descansoEquipoDao, never()).eliminar(anyLong());
    }

    // ---- confirmar ----------------------------------------------------------------

    @Test
    void confirmarCreaLosPartidosNuevosComoLigaFfcvConJornada() {
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.creados()).isEqualTo(2);

        ArgumentCaptor<Partido> captor = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao, org.mockito.Mockito.times(2)).crear(captor.capture());

        Partido filial = captor.getAllValues().stream()
                .filter(p -> p.getEquipoId() == FILIAL).findFirst().orElseThrow();
        assertThat(filial.getTipo()).isEqualTo("LIGA");
        assertThat(filial.getOrigen()).isEqualTo("FFCV");
        assertThat(filial.getJornada()).isEqualTo(4);
        assertThat(filial.getRival()).isEqualTo("C.F. Calpe");
        assertThat(filial.getCampo()).isEqualTo("Camp Nou");
        assertThat(filial.getHora()).isEqualTo("19:30");
        assertThat(filial.getDia()).isEqualTo(java.sql.Date.valueOf(SABADO));
        assertThat(filial.getResultado()).isNull();
    }

    @Test
    void confirmarActualizaSoloHoraCampoRivalYJornadaSinTocarResultadoNiBorrarNada() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "C.F. Calpe", "18:00", "Camp Nou", 4));
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.actualizados()).isEqualTo(1);

        ArgumentCaptor<Partido> captor = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao).actualizarDatosFfcv(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(50L);
        assertThat(captor.getValue().getHora()).isEqualTo("19:30");
        assertThat(captor.getValue().getResultado()).isNull();

        // Nunca el update general (que escribiría RESULTADO/goles), ni borrar/cancelar.
        verify(partidoDao, never()).actualizar(any());
        verify(partidoDao, never()).eliminar(anyLong());
        verify(partidoDao, never()).cancelar(anyLong());
    }

    @Test
    void confirmarNoToqueLosPartidosSinCambiosNiLosConflictosManuales() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "C.F. Calpe", "19:30", "Camp Nou", 4));
        partidos.add(partido(60L, PRIMER_EQUIPO, DOMINGO, "MANUAL", "Amistoso", "12:00", "X", null));
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.sinCambios()).isEqualTo(1);
        assertThat(resultado.conflictosManuales()).isEqualTo(1);
        assertThat(resultado.avisos()).anyMatch(a -> a.contains("partido manual"));
        verify(partidoDao, never()).crear(any());
        verify(partidoDao, never()).actualizarDatosFfcv(any());
    }

    @Test
    void confirmarNoBorraNiCambiaLosPartidosQueYaNoAparecenYLosDejaComoAviso() {
        partidos.add(partido(70L, JUVENIL_A, SABADO, "FFCV", "Rival Juvenil", "16:00", "Campo", 4));
        when(convocatoriaDao.obtenerPorPartidoId(70L)).thenReturn(new Convocatoria());
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.avisos()).anyMatch(a -> a.contains("Rival Juvenil") && a.contains("convocatoria"));
        verify(partidoDao, never()).eliminar(anyLong());
        verify(partidoDao, never()).cancelar(anyLong());
        verify(partidoDao, never()).actualizar(any());
        verify(partidoDao, never()).actualizarDatosFfcv(org.mockito.ArgumentMatchers.argThat(
                p -> p != null && java.util.Objects.equals(p.getId(), 70L)));
    }

    @Test
    void confirmarCreaElDescansoDeLosEquiposQueNoJueganSoloUnaVez() {
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.descansosCreados()).isEqualTo(1);

        ArgumentCaptor<DescansoEquipo> captor = ArgumentCaptor.forClass(DescansoEquipo.class);
        verify(descansoEquipoDao).crear(captor.capture());
        assertThat(captor.getValue().getEquipoId()).isEqualTo(JUVENIL_A);
        assertThat(captor.getValue().getFecha()).isEqualTo(SABADO);
    }

    @Test
    void siElEquipoYaTeniaDescansoEseFinDeSemanaNoSeDuplica() {
        descansos.add(new DescansoEquipo(5L, JUVENIL_A, SABADO, null));
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.descansosCreados()).isZero();
        verify(descansoEquipoDao, never()).crear(any());
        verify(descansoEquipoDao, never()).eliminar(anyLong());
    }

    @Test
    void siUnEquipoTeniaDescansoYAhoraTraePartidoSeBorraElDescanso() {
        descansos.add(new DescansoEquipo(5L, FILIAL, SABADO, null));
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.descansosEliminados()).isEqualTo(1);
        verify(descansoEquipoDao).eliminar(5L);
    }

    @Test
    void confirmarGuardaLasRelacionesNuevasYImportaEsasFilas() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                partidoCsv("Mutxamel C.F. \"A\"", true, "Rival", "Campo Municipal", "10:00", 3));
        PrevisualizacionFfcv previa = service.previsualizar(archivo);
        assertThat(previa.filas().get(0).estado()).isEqualTo(EstadoFilaFfcv.SIN_EQUIPO);

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa,
                List.of(new AsignacionEquipoFfcv(CADETE, "A", CADETE_A)));

        ArgumentCaptor<EquipoFfcv> relacion = ArgumentCaptor.forClass(EquipoFfcv.class);
        verify(equipoFfcvDao).crear(relacion.capture());
        assertThat(relacion.getValue().getEquipoId()).isEqualTo(CADETE_A);
        assertThat(relacion.getValue().getCompeticion()).isEqualTo(CADETE);
        assertThat(relacion.getValue().getLetra()).isEqualTo("A");

        assertThat(resultado.creados()).isEqualTo(1);
        assertThat(resultado.sinEquipo()).isZero();

        ArgumentCaptor<Partido> partido = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao).crear(partido.capture());
        assertThat(partido.getValue().getEquipoId()).isEqualTo(CADETE_A);
    }

    @Test
    void lasFilasSinEquipoSinAsignarSeSaltanYSeAvisa() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                partidoCsv("Mutxamel C.F. \"A\"", true, "Rival", "Campo Municipal", "10:00", 3),
                " Segona FFCV, Grup - 7" + CABECERA,
                partidoCsv("Mutxamel C.F. \"B\"", true, "C.F. Calpe", "Camp Nou", "19:30", 4));
        PrevisualizacionFfcv previa = service.previsualizar(archivo);

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", previa, List.of());

        assertThat(resultado.sinEquipo()).isEqualTo(1);
        assertThat(resultado.creados()).isEqualTo(1);
        assertThat(resultado.avisos()).anyMatch(a -> a.contains("Sin equipo"));
        verify(equipoFfcvDao, never()).crear(any());
    }

    @Test
    void noSeAceptaUnaRelacionQueNoCorrespondeAAlgunaFilaDelArchivo() {
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        assertThatThrownBy(() -> service.confirmar("coordinador", previa,
                List.of(new AsignacionEquipoFfcv("Otra competición", null, CADETE_A))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(partidoDao, never()).crear(any());
    }

    @Test
    void noSeAceptaUnaRelacionConUnEquipoInexistente() {
        byte[] archivo = csv(
                "  17-10-2026, (Sábado)",
                " Primera Cadet, Grup - 10" + CABECERA,
                partidoCsv("Mutxamel C.F. \"A\"", true, "Rival", "Campo Municipal", "10:00", 3));
        PrevisualizacionFfcv previa = service.previsualizar(archivo);

        assertThatThrownBy(() -> service.confirmar("coordinador", previa,
                List.of(new AsignacionEquipoFfcv(CADETE, "A", 999L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elEstadoQueVuelveDelNavegadorNoSeFiaSeRecalcula() {
        PrevisualizacionFfcv previa = service.previsualizar(archivoBasico());

        List<FfcvFilaPrevisualizacion> manipuladas = previa.filas().stream()
                .map(f -> new FfcvFilaPrevisualizacion(f.fecha(), f.competicion(), f.letra(), f.local(), f.rival(),
                        f.campo(), f.hora(), f.jornada(), EstadoFilaFfcv.SIN_CAMBIOS, 999L, "Falso", 12345L,
                        List.of()))
                .toList();

        PrevisualizacionFfcv trucada = new PrevisualizacionFfcv(previa.desde(), previa.hasta(), manipuladas,
                List.of(), List.of(), List.of());

        ResultadoImportacionFfcv resultado = service.confirmar("coordinador", trucada, List.of());

        assertThat(resultado.creados()).isEqualTo(2);

        ArgumentCaptor<Partido> captor = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao, org.mockito.Mockito.times(2)).crear(captor.capture());
        assertThat(captor.getAllValues()).extracting(Partido::getEquipoId)
                .containsExactlyInAnyOrder(FILIAL, PRIMER_EQUIPO);
        assertThat(captor.getAllValues()).extracting(Partido::getId).containsOnlyNulls();
    }

    @Test
    void confirmarSinFilasSeRechaza() {
        assertThatThrownBy(() -> service.confirmar("u", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirmar("u",
                new PrevisualizacionFfcv(null, null, List.of(), List.of(), List.of(), List.of()), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- recordatorio ---------------------------------------------------------------

    @Test
    void hayImportacionSiExistenPartidosFfcvEseFinDeSemana() {
        partidos.add(partido(50L, FILIAL, SABADO, "FFCV", "C.F. Calpe", "19:30", "Camp Nou", 4));

        assertThat(service.hayImportacionParaFinDeSemana(SABADO)).isTrue();
    }

    @Test
    void hayImportacionSiSoloExistenDescansosEseFinDeSemana() {
        descansos.add(new DescansoEquipo(1L, FILIAL, SABADO, null));

        assertThat(service.hayImportacionParaFinDeSemana(SABADO)).isTrue();
    }

    @Test
    void losPartidosManualesNoCuentanComoImportacion() {
        partidos.add(partido(50L, FILIAL, SABADO, "MANUAL", "Amistoso", "19:30", "Camp Nou", null));

        assertThat(service.hayImportacionParaFinDeSemana(SABADO)).isFalse();
    }

    @Test
    void sinNadaEseFinDeSemanaNoHayImportacion() {
        partidos.add(partido(50L, FILIAL, SABADO.minusDays(7), "FFCV", "C.F. Calpe", "19:30", "Camp Nou", 3));

        assertThat(service.hayImportacionParaFinDeSemana(SABADO)).isFalse();
    }

}
