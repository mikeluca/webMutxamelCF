package com.mikedev.mutxamelcf.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class PeriodosCuotasTest {

	@Test
	void esMensualSiElNombreLlevaCuota() {
		assertThat(PeriodosCuotas.esCuotaMensual("Cuota mensual F8")).isTrue();
		assertThat(PeriodosCuotas.esCuotaMensual("  cuota ")).isTrue();
		assertThat(PeriodosCuotas.esCuotaMensual("Matrícula")).isFalse();
		assertThat(PeriodosCuotas.esCuotaMensual(null)).isFalse();
	}

	@Test
	void losLimitesSalenDelNombreDeLaTemporadaOSuFechaDeInicio() {
		assertThat(PeriodosCuotas.periodosTemporada("2026/2027", null)).containsExactly("2026-09", "2027-06");
		assertThat(PeriodosCuotas.periodosTemporada("Temporada 2026-2027", null)).containsExactly("2026-09", "2027-06");
		assertThat(PeriodosCuotas.periodosTemporada("Sin años", Date.valueOf("2025-07-01")))
				.containsExactly("2025-09", "2026-06");
	}

	@Test
	void generaLosMesesDelRangoYRechazaLosNoValidos() {
		assertThat(PeriodosCuotas.generar("2026-11", "2027-01")).containsExactly("2026-11", "2026-12", "2027-01");
		assertThat(PeriodosCuotas.generar("2027-01", "2026-11")).isEmpty();
		assertThat(PeriodosCuotas.generar("2026-01", "2029-01")).isEmpty();
		assertThat(PeriodosCuotas.generar("x", "2026-11")).isEmpty();
		assertThat(PeriodosCuotas.generar(null, "2026-11")).isEmpty();
	}

	@Test
	void desdeElMesDeLaAsignacion() {
		List<String> todos = PeriodosCuotas.generar("2026-09", "2027-06");

		assertThat(PeriodosCuotas.generarDesde("2026-09", "2027-06", YearMonth.of(2026, 8))).isEqualTo(todos);
		assertThat(PeriodosCuotas.generarDesde("2026-09", "2027-06", YearMonth.of(2026, 9))).isEqualTo(todos);
		assertThat(PeriodosCuotas.generarDesde("2026-09", "2027-06", YearMonth.of(2027, 2))).containsExactly("2027-02",
				"2027-03", "2027-04", "2027-05", "2027-06");
		assertThat(PeriodosCuotas.generarDesde("2026-09", "2027-06", YearMonth.of(2027, 7))).isEmpty();
		assertThat(PeriodosCuotas.generarDesde("2026-09", "2027-06", null)).isEqualTo(todos);
	}
}
