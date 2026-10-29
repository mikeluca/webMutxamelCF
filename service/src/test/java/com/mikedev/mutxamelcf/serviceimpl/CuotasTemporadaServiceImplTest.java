package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService.ResultadoRetiradaCuotas;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

class CuotasTemporadaServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final long TEMPORADA = 5L;
	private static final long MENSUAL = 100L;
	private static final long MATRICULA = 101L;

	private TemporadaService temporadaService;
	private ConceptoPagoService conceptoPagoService;
	private CuotaJugadorService cuotaJugadorService;
	private PagoService pagoService;
	private List<CuotaJugadorDTO> guardadas;

	@BeforeEach
	void setUp() {
		temporadaService = mock(TemporadaService.class);
		conceptoPagoService = mock(ConceptoPagoService.class);
		cuotaJugadorService = mock(CuotaJugadorService.class);
		pagoService = mock(PagoService.class);
		guardadas = new ArrayList<>();

		TemporadaDTO activa = new TemporadaDTO();
		activa.setId(TEMPORADA);
		activa.setNombre("2026/2027");
		activa.setFechaInicio(Date.valueOf("2026-07-01"));
		when(temporadaService.obtenerTemporadaActiva()).thenReturn(activa);

		when(conceptoPagoService.obtenerActivosPorTemporada(TEMPORADA))
				.thenReturn(List.of(concepto(MENSUAL, "Cuota mensual", "30"), concepto(MATRICULA, "Matrícula", "60")));
		when(cuotaJugadorService.guardarCuota(any())).thenAnswer(i -> guardadas.add(i.getArgument(0)));
	}

	private static ConceptoPagoDTO concepto(long id, String nombre, String importe) {
		ConceptoPagoDTO c = new ConceptoPagoDTO();
		c.setId(id);
		c.setTemporadaId(TEMPORADA);
		c.setNombre(nombre);
		c.setImporte(importe == null ? null : new BigDecimal(importe));
		return c;
	}

	private static CuotaJugadorDTO cuota(long id, long concepto, String periodo) {
		CuotaJugadorDTO c = new CuotaJugadorDTO();
		c.setId(id);
		c.setConceptoPagoId(concepto);
		c.setPeriodo(periodo);
		return c;
	}

	private CuotasTemporadaServiceImpl servicioEl(String dia) {
		Clock reloj = Clock.fixed(LocalDate.parse(dia).atTime(10, 0).atZone(MADRID).toInstant(), MADRID);

		return new CuotasTemporadaServiceImpl(temporadaService, conceptoPagoService, cuotaJugadorService, pagoService,
				reloj);
	}

	@Test
	void asignadoEnSeptiembreOAntesSeCobraLaTemporadaEntera() {
		for (String dia : List.of("2026-08-20", "2026-09-14")) {
			guardadas.clear();

			int creadas = servicioEl(dia).crearCuotasDelJugador(1L);

			// 10 meses (septiembre a junio) + la matrícula
			assertThat(creadas).isEqualTo(11);
			assertThat(guardadas).filteredOn(c -> c.getConceptoPagoId() == MENSUAL).extracting(CuotaJugadorDTO::getPeriodo)
					.first().isEqualTo("2026-09");
			assertThat(guardadas).filteredOn(c -> c.getConceptoPagoId() == MENSUAL).hasSize(10);
		}
	}

	@Test
	void asignadoDespuesDeSeptiembreSeCobraDesdeElMesDeLaAsignacion() {
		int creadas = servicioEl("2026-10-27").crearCuotasDelJugador(1L);

		// octubre a junio (9 meses) + la matrícula
		assertThat(creadas).isEqualTo(10);
		assertThat(guardadas).filteredOn(c -> c.getConceptoPagoId() == MENSUAL).extracting(CuotaJugadorDTO::getPeriodo)
				.first().isEqualTo("2026-10");
		assertThat(guardadas).extracting(CuotaJugadorDTO::getPeriodo).doesNotContain("2026-09");
	}

	@Test
	void lasCuotasNacenPendientesConElImporteDelConceptoYLaMatriculaSinPeriodo() {
		servicioEl("2026-09-14").crearCuotasDelJugador(7L);

		CuotaJugadorDTO matricula = guardadas.stream().filter(c -> c.getConceptoPagoId() == MATRICULA).findFirst().get();
		assertThat(matricula.getPeriodo()).isNull();
		assertThat(matricula.getImporte()).isEqualByComparingTo("60");
		assertThat(matricula.getEstado()).isEqualTo("PENDIENTE");
		assertThat(matricula.getJugadorId()).isEqualTo(7L);
	}

	@Test
	void noDuplicaLasCuotasQueYaTiene() {
		when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(cuota(1, MENSUAL, "2026-10"),
				cuota(2, MENSUAL, "2026-11"), cuota(3, MATRICULA, null)));

		int creadas = servicioEl("2026-10-27").crearCuotasDelJugador(1L);

		// octubre y noviembre ya estaban: faltan diciembre a junio (7)
		assertThat(creadas).isEqualTo(7);
		assertThat(guardadas).extracting(CuotaJugadorDTO::getPeriodo).doesNotContain("2026-10", "2026-11");
		assertThat(guardadas).noneMatch(c -> c.getConceptoPagoId() == MATRICULA);
	}

	@Test
	void repetirNoCreaNada() {
		when(cuotaJugadorService.obtenerPorJugador(1L)).thenAnswer(i -> {
			List<CuotaJugadorDTO> existentes = new ArrayList<>();
			guardadas.forEach(g -> existentes.add(cuota(9, g.getConceptoPagoId(), g.getPeriodo())));
			return existentes;
		});
		CuotasTemporadaServiceImpl servicio = servicioEl("2026-09-14");

		assertThat(servicio.crearCuotasDelJugador(1L)).isEqualTo(11);
		assertThat(servicio.crearCuotasDelJugador(1L)).isZero();
	}

	@Test
	void unConceptoSinImporteNoGeneraCuotas() {
		when(conceptoPagoService.obtenerActivosPorTemporada(TEMPORADA))
				.thenReturn(List.of(concepto(MATRICULA, "Matrícula", null)));

		assertThat(servicioEl("2026-09-14").crearCuotasDelJugador(1L)).isZero();
	}

	@Test
	void sinTemporadaActivaOSinJugadorNoHaceNada() {
		when(temporadaService.obtenerTemporadaActiva()).thenReturn(null);

		assertThat(servicioEl("2026-09-14").crearCuotasDelJugador(1L)).isZero();
		assertThat(servicioEl("2026-09-14").crearCuotasDelJugador(null)).isZero();
		verify(cuotaJugadorService, never()).guardarCuota(any());
	}

	// ---- retirar --------------------------------------------------------------------------------

	@Test
	void retirarBorraLasCuotasSinPagosYConservaLasQueTienenPagos() {
		when(conceptoPagoService.obtenerPorTemporada(TEMPORADA))
				.thenReturn(List.of(concepto(MENSUAL, "Cuota mensual", "30"), concepto(MATRICULA, "Matrícula", "60")));
		when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(cuota(1, MENSUAL, "2026-10"),
				cuota(2, MENSUAL, "2026-11"), cuota(3, MATRICULA, null)));
		PagoDTO pago = new PagoDTO();
		pago.setCuotaJugadorId(2L);
		when(pagoService.obtenerPorCuotas(List.of(1L, 2L, 3L))).thenReturn(List.of(pago));

		ResultadoRetiradaCuotas resultado = servicioEl("2026-10-27").retirarCuotasDelJugador(1L);

		assertThat(resultado.borradas()).isEqualTo(2);
		assertThat(resultado.conservadasConPagos()).isEqualTo(1);
		assertThat(resultado.cuotasConservadas()).containsExactly(2L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Long>> borradas = ArgumentCaptor.forClass(List.class);
		verify(cuotaJugadorService).eliminarEnLote(borradas.capture());
		assertThat(borradas.getValue()).containsExactly(1L, 3L);
	}

	@Test
	void retirarNoTocaLasCuotasDeOtrasTemporadas() {
		when(conceptoPagoService.obtenerPorTemporada(TEMPORADA)).thenReturn(List.of(concepto(MENSUAL, "Cuota", "30")));
		when(cuotaJugadorService.obtenerPorJugador(1L))
				.thenReturn(List.of(cuota(1, MENSUAL, "2026-10"), cuota(8, 999L, "2025-10")));

		servicioEl("2026-10-27").retirarCuotasDelJugador(1L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Long>> borradas = ArgumentCaptor.forClass(List.class);
		verify(cuotaJugadorService).eliminarEnLote(borradas.capture());
		assertThat(borradas.getValue()).containsExactly(1L);
	}

	@Test
	void retirarSinCuotasONadaQueRetirarNoBorraNada() {
		when(conceptoPagoService.obtenerPorTemporada(TEMPORADA)).thenReturn(List.of(concepto(MENSUAL, "Cuota", "30")));
		when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of());

		ResultadoRetiradaCuotas resultado = servicioEl("2026-10-27").retirarCuotasDelJugador(1L);

		assertThat(resultado.borradas()).isZero();
		assertThat(resultado.conservadasConPagos()).isZero();
		verify(cuotaJugadorService, never()).eliminarEnLote(any());
	}
}
