package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService;

@Service
public class RecordatorioEntrenadorServiceImpl implements RecordatorioEntrenadorService {

	/** Un recordatorio por partido y tipo en este tiempo. */
	static final Duration INTERVALO = Duration.ofHours(2);

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final PartidoDao partidoDao;
	private final ConvocatoriaDao convocatoriaDao;
	private final EquipoGestionDao equipoGestionDao;
	private final ComunicacionService comunicacionService;
	private final Clock clock;
	private final TextosAvisos textos;
	private final IdiomasUsuarios idiomas;

	/** Cuándo se envió el último recordatorio de cada partido y tipo. En memoria: se pierde al reiniciar. */
	private final Map<String, LocalDateTime> ultimos = new ConcurrentHashMap<>();

	public RecordatorioEntrenadorServiceImpl(
			PartidoDao partidoDao,
			ConvocatoriaDao convocatoriaDao,
			EquipoGestionDao equipoGestionDao,
			ComunicacionService comunicacionService,
			Clock clock,
			TextosAvisos textos,
			IdiomasUsuarios idiomas) {

		this.textos = textos;
		this.idiomas = idiomas;

		this.partidoDao = partidoDao;
		this.convocatoriaDao = convocatoriaDao;
		this.equipoGestionDao = equipoGestionDao;
		this.comunicacionService = comunicacionService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Enviado enviar(Long usuarioAppId, Long partidoId, String tipo) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (!equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
			throw new SecurityException("Solo coordinación puede enviar recordatorios");
		}

		String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();

		if (!TIPO_CONVOCATORIA.equals(tipoNormalizado) && !TIPO_RESULTADO.equals(tipoNormalizado)) {
			throw new IllegalArgumentException("El tipo debe ser CONVOCATORIA o RESULTADO");
		}

		Partido partido = partidoId == null || partidoId <= 0 ? null : partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		LocalDateTime ahora = LocalDateTime.now(clock);

		boolean esConvocatoria = TIPO_CONVOCATORIA.equals(tipoNormalizado);

		if (esConvocatoria) {
			validarConvocatoriaPendiente(partido, ahora.toLocalDate());
		} else if (!CriterioResultadoPartido.estaPendiente(partido, ahora)) {
			// El mismo criterio que el panel de fin de semana y el recordatorio automático.
			throw new IllegalArgumentException("El partido no está pendiente de resultado");
		}

		String clave = partido.getId() + ":" + tipoNormalizado;

		purgarCaducados(ahora);

		List<Long> todos = equipoGestionDao.obtenerEntrenadoresPorEquipo(partido.getEquipoId());

		// El propio coordinador no es destinatario aunque también entrene al equipo.
		List<Long> entrenadores = new ArrayList<>();

		if (todos != null) {
			for (Long id : todos) {
				if (id != null && !id.equals(usuarioAppId)) {
					entrenadores.add(id);
				}
			}
		}

		if (entrenadores.isEmpty()) {

			if (todos != null && !todos.isEmpty()) {
				throw SinEntrenadores.soloElCoordinador();
			}

			throw new SinEntrenadores();
		}

		// Reserva atómica ANTES de enviar: dos peticiones a la vez no pasan las dos.
		LocalDateTime[] bloqueado = new LocalDateTime[1];

		ultimos.compute(clave, (k, previo) -> {
			if (previo != null && ahora.isBefore(previo.plus(INTERVALO))) {
				bloqueado[0] = previo;
				return previo;
			}
			return ahora;
		});

		if (bloqueado[0] != null) {
			throw new DemasiadoPronto(bloqueado[0]);
		}

		LocalDate dia = PlazoJustificacionPartido.dia(partido);
		String rival = partido.getRival();
		boolean conHora = partido.getHora() != null && !partido.getHora().isBlank();

		Map<String, String> datos = esConvocatoria
				? datosPush(PUSH_CONVOCATORIA_PENDIENTE, partido, dia)
				: datosPush(PUSH_RESULTADO_PENDIENTE, partido, dia);

		// El remitente es el coordinador que lo pide.
		try {
			idiomas.crearPrivadaPorIdioma(comunicacionService, entrenadores, usuarioAppId, datos, idioma -> {

				String fecha = textos.fecha(idioma, dia);

				if (!esConvocatoria) {
					return new TextoAviso(textos.texto(idioma, "recordatorioResultado.titulo"),
							textos.texto(idioma, "recordatorioResultado.texto", "rival", rival, "fecha", fecha));
				}

				return new TextoAviso(textos.texto(idioma, "recordatorioConvocatoria.titulo"),
						conHora
								? textos.texto(idioma, "recordatorioConvocatoria.texto", "rival", rival,
										"fecha", fecha, "hora", TextosConvocatoria.hora(textos, partido.getHora()))
								: textos.texto(idioma, "recordatorioConvocatoria.textoSinHora", "rival", rival,
										"fecha", fecha));
			});
		} catch (RuntimeException e) {
			// Si no se ha enviado no gasta el límite (solo se libera si la reserva sigue siendo la nuestra).
			ultimos.remove(clave, ahora);
			throw e;
		}

		return new Enviado(entrenadores.size(), ahora);
	}

	private void purgarCaducados(LocalDateTime ahora) {
		ultimos.values().removeIf(v -> !ahora.isBefore(v.plus(INTERVALO)));
	}

	/*
	 * El data del push: con el partido y su equipo la app abre el formulario sin pedir nada más
	 * (convocatoria preseleccionada o resultado del partido).
	 */
	private Map<String, String> datosPush(String tipo, Partido partido, LocalDate dia) {

		Map<String, String> datos = new HashMap<>();

		datos.put("tipo", tipo);
		datos.put("partidoId", String.valueOf(partido.getId()));
		datos.put("equipoId", String.valueOf(partido.getEquipoId()));
		datos.put("local", String.valueOf(partido.isLocal()));

		poner(datos, "equipo", equipoGestionDao.obtenerNombreEquipo(partido.getEquipoId()));
		poner(datos, "rival", partido.getRival());
		poner(datos, "hora", partido.getHora());
		poner(datos, "fecha", dia == null ? null : dia.toString());

		return datos;
	}

	private static void poner(Map<String, String> datos, String clave, String valor) {

		if (valor != null && !valor.isBlank()) {
			datos.put(clave, valor.trim());
		}
	}

	/* Solo si no tiene convocatoria, no está cancelado y no ha pasado. */
	private void validarConvocatoriaPendiente(Partido partido, LocalDate hoy) {

		if (partido.isCancelado()) {
			throw new IllegalArgumentException("El partido está cancelado");
		}

		LocalDate dia = PlazoJustificacionPartido.dia(partido);

		if (dia != null && dia.isBefore(hoy)) {
			throw new IllegalArgumentException("El partido ya ha pasado");
		}

		if (convocatoriaDao.obtenerPorPartidoId(partido.getId()) != null) {
			throw new IllegalArgumentException("El partido ya tiene convocatoria");
		}
	}
}
