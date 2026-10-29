package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mikedev.mutxamelcf.service.ContinuidadService;

class ContinuidadSchedulerTest {

	private final ContinuidadService servicio = mock(ContinuidadService.class);
	private final ContinuidadScheduler scheduler = new ContinuidadScheduler(servicio);

	@Test
	void elJobDeMayoDelegaEnElServicio() {
		scheduler.pedirConfirmacion();

		verify(servicio).pedirConfirmacionSiHaySiguiente();
	}

	@Test
	void elJobSemanalDelegaEnElServicio() {
		scheduler.recordarPendientes();

		verify(servicio).recordarPendientes();
	}

	@Test
	void unFalloNoSePropagaParaNoDetenerElSiguienteDisparo() {
		when(servicio.pedirConfirmacionSiHaySiguiente()).thenThrow(new IllegalStateException("fallo"));
		when(servicio.recordarPendientes()).thenThrow(new IllegalStateException("fallo"));

		assertThatCode(scheduler::pedirConfirmacion).doesNotThrowAnyException();
		assertThatCode(scheduler::recordarPendientes).doesNotThrowAnyException();
	}

	@Test
	void losCronsSonElUnoDeMayoYElLunesEnHoraDeMadrid() throws Exception {
		Scheduled mayo = anotacion("pedirConfirmacion");
		Scheduled lunes = anotacion("recordarPendientes");

		assertThat(mayo.cron()).isEqualTo("0 0 9 1 5 *");
		assertThat(lunes.cron()).isEqualTo("0 0 10 * * MON");
		assertThat(mayo.zone()).isEqualTo("Europe/Madrid");
		assertThat(lunes.zone()).isEqualTo("Europe/Madrid");
	}

	private static Scheduled anotacion(String metodo) throws Exception {
		Method m = ContinuidadScheduler.class.getMethod(metodo);

		return m.getAnnotation(Scheduled.class);
	}
}
