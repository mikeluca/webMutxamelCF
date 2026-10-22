package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.EstadoAppDao;
import com.mikedev.mutxamelcf.dao.EstadoAppDao.DispositivosPorVersion;
import com.mikedev.mutxamelcf.model.EstadoAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

class EstadoAppServiceImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

	private EstadoAppDao dao;
	private UsuarioAppService usuarios;
	private EstadoAppServiceImpl service;

	@BeforeEach
	void setUp() {
		dao = mock(EstadoAppDao.class);
		usuarios = mock(UsuarioAppService.class);
		service = new EstadoAppServiceImpl(dao, usuarios, CLOCK, "1.0.5", "1.0.7");

		when(dao.contarCuentas()).thenReturn(new EstadoAppDao.Totales(40, 3, 2));
		when(dao.contarActivasSinDispositivo()).thenReturn(5);
		when(dao.contarActivasSinNotificaciones()).thenReturn(6);
		when(dao.contarInactivasDesde(any())).thenReturn(7);
	}

	@Test
	void soloAdminAppPuedeVerElEstadoYLasListas() {
		doThrow(new SecurityException("no")).when(usuarios).exigirAdminApp(9);

		assertThatThrownBy(() -> service.obtenerEstado(9)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> service.listarUsuarios(9, "INACTIVOS")).isInstanceOf(SecurityException.class);

		verify(dao, never()).contarCuentas();
	}

	@Test
	void devuelveLosTotalesYElLimiteDeInactividadDe30Dias() {
		EstadoAppResponse estado = service.obtenerEstado(1);

		assertThat(estado.cuentas()).isEqualTo(new EstadoAppResponse.Cuentas(40, 3, 2));
		assertThat(estado.activasSinDispositivo()).isEqualTo(5);
		assertThat(estado.activasSinNotificaciones()).isEqualTo(6);
		assertThat(estado.usuariosInactivos()).isEqualTo(7);
		assertThat(estado.diasInactividad()).isEqualTo(30);
		assertThat(estado.versionMinima()).isEqualTo(new EstadoAppResponse.VersionMinima("1.0.5", "1.0.7"));

		verify(dao).contarInactivasDesde(LocalDateTime.of(2026, 9, 26, 12, 0));
	}

	@Test
	void versionesOrdenadasDeMayorAMenorMarcandoLasInferioresALaMinimaDeSuPlataforma() {
		when(dao.contarDispositivosPorVersion()).thenReturn(List.of(
				new DispositivosPorVersion("ANDROID", "1.0.9", 10),
				new DispositivosPorVersion("ANDROID", "1.0.10", 4),
				new DispositivosPorVersion("ANDROID", "1.0.4", 2),
				new DispositivosPorVersion("IOS", "1.0.6", 3),
				new DispositivosPorVersion("IOS", "1.0.10", 5),
				new DispositivosPorVersion("ANDROID", null, 1)));

		EstadoAppResponse estado = service.obtenerEstado(1);

		assertThat(estado.versiones()).extracting(
				EstadoAppResponse.VersionDispositivos::plataforma,
				EstadoAppResponse.VersionDispositivos::version,
				EstadoAppResponse.VersionDispositivos::dispositivos,
				EstadoAppResponse.VersionDispositivos::porDebajoMinima)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("ANDROID", "1.0.10", 4, false),
						org.assertj.core.groups.Tuple.tuple("IOS", "1.0.10", 5, false),
						org.assertj.core.groups.Tuple.tuple("ANDROID", "1.0.9", 10, false),
						// 1.0.6 está por encima de la mínima de Android (1.0.5) pero no de la de iOS (1.0.7).
						org.assertj.core.groups.Tuple.tuple("IOS", "1.0.6", 3, true),
						org.assertj.core.groups.Tuple.tuple("ANDROID", "1.0.4", 2, true),
						org.assertj.core.groups.Tuple.tuple("ANDROID", "Desconocida", 1, false));

		// Dispositivos por debajo de la mínima: 3 de iOS 1.0.6 + 2 de Android 1.0.4.
		assertThat(estado.dispositivosVersionAntigua()).isEqualTo(5);
	}

	@Test
	void sinMinimaConfiguradaNingunDispositivoEsAntiguo() {
		service = new EstadoAppServiceImpl(dao, usuarios, CLOCK, "", null);
		when(dao.contarDispositivosPorVersion()).thenReturn(List.of(new DispositivosPorVersion("ANDROID", "0.1", 3)));

		assertThat(service.obtenerEstado(1).dispositivosVersionAntigua()).isZero();
	}

	@Test
	void listaLasCuentasDeCadaFiltroConNombreRolesYVersiones() {
		LocalDateTime acceso = LocalDateTime.of(2026, 9, 1, 10, 0);

		when(dao.listarActivasSinDispositivo(200)).thenReturn(List.of(new EstadoAppDao.Usuario(2, "u2@b.es", acceso)));
		when(dao.versionesDeDispositivosActivos(List.of(2L))).thenReturn(Map.of());
		when(usuarios.obtenerNombreMostrable(2)).thenReturn("Marta Gil");

		RolApp rol = new RolApp();
		rol.setCodigo("FAMILIAR");
		when(usuarios.obtenerRoles(2)).thenReturn(List.of(rol, rol));

		List<EstadoAppResponse.UsuarioEstado> lista = service.listarUsuarios(1, "sin_dispositivo");

		assertThat(lista).containsExactly(new EstadoAppResponse.UsuarioEstado(
				2, "Marta Gil", "u2@b.es", List.of("FAMILIAR"), acceso, List.of()));
	}

	@Test
	void sinNombreSeUsaElEmailYLasVersionesNulasSonDesconocidas() {
		when(dao.listarActivasSinNotificaciones(200)).thenReturn(List.of(new EstadoAppDao.Usuario(3, "u3@b.es", null)));
		when(dao.versionesDeDispositivosActivos(List.of(3L)))
				.thenReturn(Map.of(3L, java.util.Arrays.asList("1.0.2", null)));
		when(usuarios.obtenerNombreMostrable(3)).thenReturn(null);
		when(usuarios.obtenerRoles(anyInt())).thenReturn(List.of());

		EstadoAppResponse.UsuarioEstado usuario = service.listarUsuarios(1, "SIN_NOTIFICACIONES").get(0);

		assertThat(usuario.nombre()).isEqualTo("u3@b.es");
		assertThat(usuario.versiones()).containsExactly("1.0.2", "Desconocida");
	}

	@Test
	void versionAntiguaBuscaPorPlataformaLasVersionesInferioresALaMinima() {
		when(dao.contarDispositivosPorVersion()).thenReturn(List.of(
				new DispositivosPorVersion("ANDROID", "1.0.9", 10),
				new DispositivosPorVersion("ANDROID", "1.0.4", 2),
				new DispositivosPorVersion("ANDROID", "1.0.3", 1),
				new DispositivosPorVersion("IOS", "1.0.6", 3)));
		when(dao.listarActivasConVersiones(eq("ANDROID"), eq(java.util.Set.of("1.0.3", "1.0.4")), eq(200)))
				.thenReturn(List.of(new EstadoAppDao.Usuario(7, "u7@b.es", null)));
		when(dao.listarActivasConVersiones(eq("IOS"), eq(java.util.Set.of("1.0.6")), eq(200)))
				.thenReturn(List.of(new EstadoAppDao.Usuario(7, "u7@b.es", null), new EstadoAppDao.Usuario(8, "u8@b.es", null)));
		when(dao.versionesDeDispositivosActivos(any())).thenReturn(Map.of());
		when(usuarios.obtenerRoles(anyInt())).thenReturn(List.of());

		List<EstadoAppResponse.UsuarioEstado> lista = service.listarUsuarios(1, "VERSION_ANTIGUA");

		// Sin repetir a quien tiene dispositivos antiguos en las dos plataformas.
		assertThat(lista).extracting(EstadoAppResponse.UsuarioEstado::id).containsExactly(7L, 8L);
		verify(dao, never()).listarActivasConVersiones(eq("ANDROID"), eq(java.util.Set.of("1.0.9")), anyInt());
	}

	@Test
	void inactivosUsaElLimiteDe30Dias() {
		when(dao.listarInactivasDesde(any(), eq(200))).thenReturn(List.of());
		when(dao.versionesDeDispositivosActivos(any())).thenReturn(Map.of());

		service.listarUsuarios(1, "INACTIVOS");

		verify(dao).listarInactivasDesde(LocalDateTime.of(2026, 9, 26, 12, 0), 200);
	}

	@Test
	void unFiltroDesconocidoEsError() {
		assertThatThrownBy(() -> service.listarUsuarios(1, "OTRO")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.listarUsuarios(1, null)).isInstanceOf(IllegalArgumentException.class);
	}
}
