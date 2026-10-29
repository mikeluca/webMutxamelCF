package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.CuentaSinJugadoresDao;
import com.mikedev.mutxamelcf.model.CuentaSinJugadores;
import com.mikedev.mutxamelcf.service.AvisosCuentaEmail;
import com.mikedev.mutxamelcf.service.CuentasSinJugadoresService.ResultadoCuentasSinJugadores;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

class CuentasSinJugadoresServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2027-03-10T03:00:00Z"), MADRID);

	private CuentaSinJugadoresDao dao;
	private NotificacionAppService notificaciones;
	private FcmPushService push;
	private AvisosCuentaEmail email;
	private CuentasSinJugadoresServiceImpl servicio;

	@BeforeEach
	void setUp() {
		dao = mock(CuentaSinJugadoresDao.class);
		notificaciones = mock(NotificacionAppService.class);
		push = mock(FcmPushService.class);
		email = mock(AvisosCuentaEmail.class);
		servicio = new CuentasSinJugadoresServiceImpl(dao, notificaciones, push, email, RELOJ);

		when(dao.listarSinJugadoresActivos(any())).thenReturn(List.of());
		when(dao.listarMarcadas()).thenReturn(List.of());
		when(notificaciones.puedeRecibir(anyLong(), anyString())).thenReturn(true);
	}

	private static CuentaSinJugadores cuenta(long id, boolean activa, String aviso, boolean porEsto) {
		return new CuentaSinJugadores(id, "u" + id + "@x.es", "Ana", activa, aviso == null ? null : Timestamp.valueOf(aviso),
				porEsto);
	}

	private void sinJugadores(CuentaSinJugadores... cuentas) {
		when(dao.listarSinJugadoresActivos(LocalDate.of(2027, 3, 10))).thenReturn(List.of(cuentas));
	}

	private void marcadas(CuentaSinJugadores... cuentas) {
		when(dao.listarMarcadas()).thenReturn(List.of(cuentas));
	}

	@Test
	void unaCuentaActivaSinAvisoSeAvisaPorPushYEmailUnaSolaVez() {
		sinJugadores(cuenta(1, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenReturn(1);

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r).isEqualTo(new ResultadoCuentasSinJugadores(1, 0, 0, 0));
		verify(dao).marcarAviso(1L, Timestamp.valueOf("2027-03-10 04:00:00"));
		verify(notificaciones).crear(eq(1L), eq("CUENTA_SIN_JUGADORES"), eq("Tu cuenta se desactivará"),
				org.mockito.ArgumentMatchers.contains("10/06/2027"), eq(1L));
		verify(push).enviarNotificacionAUsuario(eq(1L), eq("CUENTA_SIN_JUGADORES"), anyString(), anyString(), eq(1L),
				eq(Map.of("fechaDesactivacion", "2027-06-10")));
		verify(email).enviarAvisoCuentaSinJugadores("u1@x.es", "Ana", LocalDate.of(2027, 6, 10));
	}

	@Test
	void unaCuentaYaAvisadaNoRecibeOtroAviso() {
		sinJugadores(cuenta(1, true, "2027-02-01 04:00:00", false));

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r).isEqualTo(new ResultadoCuentasSinJugadores(0, 0, 0, 0));
		verify(dao, never()).marcarAviso(anyLong(), any());
		verify(email, never()).enviarAvisoCuentaSinJugadores(anyString(), any(), any());
		verify(dao, never()).desactivar(anyLong());
	}

	@Test
	void siOtroProcesoYaLaAvisoNoSeEnvianDosAvisos() {
		sinJugadores(cuenta(1, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenReturn(0);

		assertThat(servicio.procesar().avisadas()).isZero();
		verify(email, never()).enviarAvisoCuentaSinJugadores(anyString(), any(), any());
	}

	@Test
	void sinEmailSoloSeAvisaPorPush() {
		sinJugadores(new CuentaSinJugadores(1, "  ", null, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenReturn(1);

		assertThat(servicio.procesar().avisadas()).isEqualTo(1);
		verify(email, never()).enviarAvisoCuentaSinJugadores(anyString(), any(), any());
		verify(push).enviarNotificacionAUsuario(eq(1L), anyString(), anyString(), anyString(), anyLong(), any());
	}

	@Test
	void siNoAceptaAvisosNoHayPushPeroElEmailSiSale() {
		sinJugadores(cuenta(1, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenReturn(1);
		when(notificaciones.puedeRecibir(1L, "COMUNICACION")).thenReturn(false);

		servicio.procesar();

		verify(push, never()).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());
		verify(email).enviarAvisoCuentaSinJugadores(eq("u1@x.es"), any(), any());
	}

	@Test
	void unFalloDelPushNoImpideElEmailNiLaMarca() {
		sinJugadores(cuenta(1, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenReturn(1);
		doThrow(new IllegalStateException("fcm")).when(push).enviarNotificacionAUsuario(anyLong(), anyString(), anyString(),
				anyString(), anyLong(), any());

		assertThat(servicio.procesar().avisadas()).isEqualTo(1);
		verify(email).enviarAvisoCuentaSinJugadores(eq("u1@x.es"), any(), any());
	}

	@Test
	void alCumplirseLosTresMesesSeDesactiva() {
		sinJugadores(cuenta(1, true, "2026-12-10 04:00:00", false), // justo 3 meses: hoy
				cuenta(2, true, "2026-12-11 04:00:00", false)); // mañana
		when(dao.desactivar(1L)).thenReturn(1);

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r.desactivadas()).isEqualTo(1);
		verify(dao).desactivar(1L);
		verify(dao, never()).desactivar(2L);
	}

	@Test
	void antesDeLosTresMesesNoSeDesactiva() {
		sinJugadores(cuenta(1, true, "2027-01-05 04:00:00", false));

		servicio.procesar();

		verify(dao, never()).desactivar(anyLong());
	}

	@Test
	void unaCuentaDesactivadaPorElJobSigueEsperandoSinReavisar() {
		sinJugadores(cuenta(1, false, "2026-12-01 04:00:00", true));

		assertThat(servicio.procesar()).isEqualTo(new ResultadoCuentasSinJugadores(0, 0, 0, 0));
		verify(dao, never()).reactivar(anyLong());
		verify(dao, never()).cancelarAviso(anyLong());
		verify(dao, never()).marcarAviso(anyLong(), any());
	}

	@Test
	void unaCuentaDesactivadaAManoNuncaSeReactivaNiSeAvisa() {
		sinJugadores(cuenta(1, false, null, false));
		marcadas();

		assertThat(servicio.procesar()).isEqualTo(new ResultadoCuentasSinJugadores(0, 0, 0, 0));
		verify(dao, never()).reactivar(anyLong());
		verify(dao, never()).marcarAviso(anyLong(), any());
		verify(dao, never()).desactivar(anyLong());
	}

	@Test
	void unaCuentaDesactivadaAManoConUnAvisoViejoPierdeElAviso() {
		sinJugadores(cuenta(1, false, "2026-10-01 04:00:00", false));
		when(dao.cancelarAviso(1L)).thenReturn(1);

		assertThat(servicio.procesar().canceladas()).isEqualTo(1);
		verify(dao, never()).reactivar(anyLong());
	}

	@Test
	void siUnAdministradorReactivaLaCuentaDelJobSeEmpiezaDeCero() {
		sinJugadores(cuenta(1, true, "2026-10-01 04:00:00", true));
		when(dao.cancelarAviso(1L)).thenReturn(1);

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r.canceladas()).isEqualTo(1);
		verify(dao, never()).desactivar(anyLong());
	}

	@Test
	void siVuelveUnJugadorActivoAntesDeLosTresMesesSeCancelaElAviso() {
		marcadas(cuenta(1, true, "2027-01-05 04:00:00", false));
		when(dao.cancelarAviso(1L)).thenReturn(1);

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r).isEqualTo(new ResultadoCuentasSinJugadores(0, 0, 0, 1));
		verify(dao, never()).reactivar(anyLong());
	}

	@Test
	void siVuelveUnJugadorActivoYLaDesactivoElJobSeReactiva() {
		marcadas(cuenta(1, false, "2026-12-01 04:00:00", true));
		when(dao.reactivar(1L)).thenReturn(1);

		ResultadoCuentasSinJugadores r = servicio.procesar();

		assertThat(r).isEqualTo(new ResultadoCuentasSinJugadores(0, 0, 1, 0));
		verify(dao, never()).cancelarAviso(anyLong());
	}

	@Test
	void unaCuentaMarcadaQueSigueSinJugadoresNoSeTocaComoSiVolviera() {
		CuentaSinJugadores c = cuenta(1, true, "2027-01-05 04:00:00", false);
		sinJugadores(c);
		marcadas(c);

		servicio.procesar();

		verify(dao, never()).cancelarAviso(anyLong());
		verify(dao, never()).reactivar(anyLong());
	}

	@Test
	void unaCuentaQueFallaNoImpideTratarLasDemas() {
		sinJugadores(cuenta(1, true, null, false), cuenta(2, true, null, false));
		when(dao.marcarAviso(eq(1L), any())).thenThrow(new IllegalStateException("bd"));
		when(dao.marcarAviso(eq(2L), any())).thenReturn(1);

		assertThat(servicio.procesar().avisadas()).isEqualTo(1);
		verify(email).enviarAvisoCuentaSinJugadores(eq("u2@x.es"), any(), any());
	}
}
