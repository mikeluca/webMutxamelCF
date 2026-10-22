package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EstadoAppDao;
import com.mikedev.mutxamelcf.dao.EstadoAppDao.DispositivosPorVersion;
import com.mikedev.mutxamelcf.model.EstadoAppResponse;
import com.mikedev.mutxamelcf.model.EstadoAppResponse.UsuarioEstado;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.service.EstadoAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@Service
public class EstadoAppServiceImpl implements EstadoAppService {

	static final String DESCONOCIDA = "Desconocida";

	private final EstadoAppDao estadoAppDao;
	private final UsuarioAppService usuarioAppService;
	private final Clock clock;
	private final String minimaAndroid;
	private final String minimaIos;

	public EstadoAppServiceImpl(
			EstadoAppDao estadoAppDao,
			UsuarioAppService usuarioAppService,
			Clock clock,
			@Value("${app.version.minima.android:}") String minimaAndroid,
			@Value("${app.version.minima.ios:}") String minimaIos) {

		this.estadoAppDao = estadoAppDao;
		this.usuarioAppService = usuarioAppService;
		this.clock = clock;
		this.minimaAndroid = minimaAndroid == null ? "" : minimaAndroid.trim();
		this.minimaIos = minimaIos == null ? "" : minimaIos.trim();
	}

	@Override
	@Transactional(readOnly = true)
	public EstadoAppResponse obtenerEstado(int solicitanteId) {

		usuarioAppService.exigirAdminApp(solicitanteId);

		EstadoAppDao.Totales totales = estadoAppDao.contarCuentas();

		List<DispositivosPorVersion> porVersion = estadoAppDao.contarDispositivosPorVersion();

		List<EstadoAppResponse.VersionDispositivos> versiones = new ArrayList<>();
		int antiguos = 0;

		for (DispositivosPorVersion fila : porVersion) {

			boolean antigua = estaPorDebajoDeLaMinima(fila.plataforma(), fila.version());

			if (antigua) {
				antiguos += fila.dispositivos();
			}

			versiones.add(new EstadoAppResponse.VersionDispositivos(
					fila.plataforma(),
					fila.version() == null || fila.version().isBlank() ? DESCONOCIDA : fila.version(),
					fila.dispositivos(),
					antigua));
		}

		// Versión descendente (la desconocida, al final) y, a igual versión, por plataforma.
		versiones.sort(Comparator
				.comparing((EstadoAppResponse.VersionDispositivos v) -> DESCONOCIDA.equals(v.version()))
				.thenComparing((a, b) -> VersionApp.comparar(b.version(), a.version()))
				.thenComparing(EstadoAppResponse.VersionDispositivos::plataforma));

		return new EstadoAppResponse(
				new EstadoAppResponse.Cuentas(totales.activas(), totales.pendientes(), totales.desactivadas()),
				estadoAppDao.contarActivasSinDispositivo(),
				estadoAppDao.contarActivasSinNotificaciones(),
				estadoAppDao.contarInactivasDesde(limiteInactividad()),
				DIAS_INACTIVIDAD,
				antiguos,
				versiones,
				new EstadoAppResponse.VersionMinima(minimaAndroid, minimaIos));
	}

	@Override
	@Transactional(readOnly = true)
	public List<UsuarioEstado> listarUsuarios(int solicitanteId, String filtro) {

		usuarioAppService.exigirAdminApp(solicitanteId);

		String clave = filtro == null ? "" : filtro.trim().toUpperCase();

		List<EstadoAppDao.Usuario> usuarios = switch (clave) {
			case FILTRO_SIN_DISPOSITIVO -> estadoAppDao.listarActivasSinDispositivo(MAXIMO_USUARIOS);
			case FILTRO_SIN_NOTIFICACIONES -> estadoAppDao.listarActivasSinNotificaciones(MAXIMO_USUARIOS);
			case FILTRO_INACTIVOS -> estadoAppDao.listarInactivasDesde(limiteInactividad(), MAXIMO_USUARIOS);
			case FILTRO_VERSION_ANTIGUA -> usuariosConVersionAntigua();
			default -> throw new IllegalArgumentException(
					"El filtro debe ser SIN_DISPOSITIVO, SIN_NOTIFICACIONES, VERSION_ANTIGUA o INACTIVOS");
		};

		Map<Long, List<String>> versiones = estadoAppDao.versionesDeDispositivosActivos(
				usuarios.stream().map(EstadoAppDao.Usuario::id).toList());

		return usuarios.stream()
				.map(u -> new UsuarioEstado(
						u.id(),
						nombreDe(u),
						u.email(),
						usuarioAppService.obtenerRoles((int) u.id()).stream()
								.map(RolApp::getCodigo).distinct().toList(),
						u.ultimoAcceso(),
						versiones.getOrDefault(u.id(), List.of()).stream()
								.map(v -> v == null || v.isBlank() ? DESCONOCIDA : v)
								.toList()))
				.toList();
	}

	/* Las cuentas con algún dispositivo activo de una versión inferior a la mínima de su plataforma. */
	private List<EstadoAppDao.Usuario> usuariosConVersionAntigua() {

		Map<String, Set<String>> antiguas = new LinkedHashMap<>();

		for (DispositivosPorVersion fila : estadoAppDao.contarDispositivosPorVersion()) {

			if (estaPorDebajoDeLaMinima(fila.plataforma(), fila.version())) {
				antiguas.computeIfAbsent(fila.plataforma(), p -> new TreeSet<>()).add(fila.version());
			}
		}

		Map<Long, EstadoAppDao.Usuario> sinRepetir = new LinkedHashMap<>();

		for (Map.Entry<String, Set<String>> entrada : antiguas.entrySet()) {

			for (EstadoAppDao.Usuario usuario : estadoAppDao.listarActivasConVersiones(
					entrada.getKey(), entrada.getValue(), MAXIMO_USUARIOS)) {

				sinRepetir.putIfAbsent(usuario.id(), usuario);
			}
		}

		return sinRepetir.values().stream().limit(MAXIMO_USUARIOS).toList();
	}

	/**
	 * Un dispositivo cuya versión no consta no se da por antiguo: no se sabe, y aparece como
	 * "Desconocida" en la tabla.
	 */
	boolean estaPorDebajoDeLaMinima(String plataforma, String version) {

		if (version == null || version.isBlank()) {
			return false;
		}

		String minima = "IOS".equalsIgnoreCase(plataforma) ? minimaIos : minimaAndroid;

		return !minima.isEmpty() && VersionApp.comparar(version, minima) < 0;
	}

	private LocalDateTime limiteInactividad() {
		return LocalDateTime.now(clock).minusDays(DIAS_INACTIVIDAD);
	}

	private String nombreDe(EstadoAppDao.Usuario usuario) {

		try {

			String nombre = usuarioAppService.obtenerNombreMostrable((int) usuario.id());

			return nombre == null || nombre.isBlank() ? usuario.email() : nombre;

		} catch (RuntimeException e) {

			return usuario.email();
		}
	}
}
