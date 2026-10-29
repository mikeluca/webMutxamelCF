package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.PurgaComunicacionesDao;
import com.mikedev.mutxamelcf.service.CuentasSinJugadoresService;
import com.mikedev.mutxamelcf.service.PurgaComunicacionesService;

class PurgaComunicacionesServiceImplTest {

	private final PurgaComunicacionesDao dao = mock(PurgaComunicacionesDao.class);
	private final PurgaComunicacionesService servicio = new PurgaComunicacionesServiceImpl(dao);

	@Test
	void sinDosTemporadasAnterioresNoHaceNada() {
		when(dao.inicioDeHaceDosTemporadas()).thenReturn(null);

		assertThat(servicio.purgar()).isEmpty();
		verify(dao, never()).purgarAnterioresA(any());
	}

	@Test
	void purgaLoAnteriorAlInicioDeHaceDosTemporadasYDevuelveLasFilasPorTabla() {
		Timestamp limite = Timestamp.valueOf("2026-07-01 00:00:00");
		Map<String, Integer> filas = new LinkedHashMap<>();
		filas.put("COMUNICACION_EQUIPO", 4);
		filas.put("COMUNICACIONES", 2);
		when(dao.inicioDeHaceDosTemporadas()).thenReturn(limite);
		when(dao.purgarAnterioresA(limite)).thenReturn(filas);

		assertThat(servicio.purgar()).isEqualTo(filas);
	}

	@Test
	void elJobNoSeParaSiUnaTareaFallaYLlamaALasDos() {
		PurgaComunicacionesService purga = mock(PurgaComunicacionesService.class);
		CuentasSinJugadoresService cuentas = mock(CuentasSinJugadoresService.class);
		when(purga.purgar()).thenThrow(new IllegalStateException("bd"));
		LimpiezaDeTemporadasScheduler job = new LimpiezaDeTemporadasScheduler(purga, cuentas);

		job.purgarComunicaciones();
		job.revisarCuentasSinJugadores();

		verify(purga).purgar();
		verify(cuentas).procesar();
	}
}
