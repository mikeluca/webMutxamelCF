package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.model.ImagenRedes;
import com.mikedev.mutxamelcf.model.ImagenesRedesResponse;
import com.mikedev.mutxamelcf.model.PartidoImagen;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.ImagenesRedesService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Fila;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Imagen;

@Service
public class ImagenesRedesServiceImpl implements ImagenesRedesService {

	private static final Logger logger = LoggerFactory.getLogger(ImagenesRedesServiceImpl.class);

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

	/** Tipo de la notificación en la bandeja (la app lo usa para abrir la sección de imágenes). */
	static final String TIPO_NOTIFICACION = PUSH_IMAGENES_REDES;

	private final ImagenRedesDao imagenRedesDao;
	private final ImagenesRedesRenderer renderer;
	private final EquipoGestionDao equipoGestionDao;
	private final NotificacionAppService notificacionAppService;
	private final FcmPushService fcmPushService;
	private final Clock clock;
	private final TextosAvisos textos;
	private final IdiomasUsuarios idiomas;
	private final Set<String> excluidos;

	public ImagenesRedesServiceImpl(
			ImagenRedesDao imagenRedesDao,
			ImagenesRedesRenderer renderer,
			EquipoGestionDao equipoGestionDao,
			NotificacionAppService notificacionAppService,
			FcmPushService fcmPushService,
			Clock clock,
			TextosAvisos textos,
			IdiomasUsuarios idiomas,
			@Value("${imagenes-redes.equipos-excluidos:}") String equiposExcluidos) {

		this.imagenRedesDao = imagenRedesDao;
		this.renderer = renderer;
		this.equipoGestionDao = equipoGestionDao;
		this.notificacionAppService = notificacionAppService;
		this.fcmPushService = fcmPushService;
		this.clock = clock;
		this.textos = textos;
		this.idiomas = idiomas;
		this.excluidos = parsearExcluidos(equiposExcluidos);
	}

	/** "Primer Equipo, Veteranos" → {"primer equipo", "veteranos"}. */
	static Set<String> parsearExcluidos(String lista) {

		Set<String> nombres = new LinkedHashSet<>();

		if (lista != null) {
			for (String nombre : lista.split(",")) {
				if (!nombre.isBlank()) {
					nombres.add(SeleccionImagenesRedes.normalizar(nombre));
				}
			}
		}

		return nombres;
	}

	// ---- API para coordinación ----------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public ImagenesRedesResponse listar(Long usuarioAppId, LocalDate fecha) {

		validarAcceso(usuarioAppId);

		return respuesta(viernesDe(fecha));
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] descargar(Long usuarioAppId, Long imagenId) {

		validarAcceso(usuarioAppId);

		byte[] png = imagenId == null ? null : imagenRedesDao.obtenerPng(imagenId);

		if (png == null) {
			throw new IllegalArgumentException("La imagen no existe");
		}

		return png;
	}

	@Override
	@Transactional
	public ImagenesRedesResponse regenerar(Long usuarioAppId, String tipo, LocalDate fecha) {

		validarAcceso(usuarioAppId);

		String tipoNormalizado = normalizarTipo(tipo);
		LocalDate viernes = viernesDe(fecha);

		generar(tipoNormalizado, viernes);

		return respuesta(viernes);
	}

	@Override
	@Transactional
	public int generarYAvisar(String tipo, LocalDate viernes) {

		String tipoNormalizado = normalizarTipo(tipo);
		int generadas = generar(tipoNormalizado, viernes);

		if (generadas == 0) {
			logger.info("Imágenes para redes {} del {}: no hay partidos que mostrar, no se avisa", tipoNormalizado,
					viernes);
			return 0;
		}

		despuesDelCommit(() -> avisar(tipoNormalizado, viernes));

		return generadas;
	}

	// ---- generación -------------------------------------------------------------------------------

	/** Sustituye las imágenes de ese tipo y fin de semana. Devuelve cuántas ha generado. */
	private int generar(String tipo, LocalDate viernes) {

		LocalDate domingo = viernes.plusDays(2);
		LocalDateTime ahora = LocalDateTime.now(clock);

		List<PartidoImagen> partidos = imagenRedesDao.partidosEntre(viernes, domingo);
		Map<String, List<PartidoImagen>> seleccion = SeleccionImagenesRedes.seleccionar(partidos, tipo, excluidos);

		int version = imagenRedesDao.versionMaxima(tipo, viernes) + 1;
		imagenRedesDao.borrar(tipo, viernes);

		Map<Long, byte[]> escudos = new HashMap<>();
		int generadas = 0;

		for (Map.Entry<String, List<PartidoImagen>> grupo : seleccion.entrySet()) {

			List<List<PartidoImagen>> paginas = SeleccionImagenesRedes.paginar(grupo.getValue());

			for (int i = 0; i < paginas.size(); i++) {

				List<Fila> filas = new ArrayList<>();

				for (PartidoImagen p : paginas.get(i)) {
					filas.add(fila(p, tipo, escudos));
				}

				byte[] png = renderer.renderizar(new Imagen(tipo, grupo.getKey(), i + 1, paginas.size(), filas));

				imagenRedesDao.insertar(tipo, grupo.getKey(), i + 1, paginas.size(), viernes, domingo, png, ahora,
						version);
				generadas++;
			}
		}

		logger.info("Imágenes para redes {} del {} generadas: {} (versión {})", tipo, viernes, generadas, version);

		return generadas;
	}

	private Fila fila(PartidoImagen p, String tipo, Map<Long, byte[]> escudos) {

		boolean resultados = TIPO_RESULTADOS.equals(tipo);
		byte[] escudo = null;

		if (p.clubRivalId() != null) {
			escudo = escudos.computeIfAbsent(p.clubRivalId(), imagenRedesDao::escudoRival);
		}

		return new Fila(
				p.equipo().trim().toUpperCase(Locale.ROOT),
				SeleccionImagenesRedes.lineaRival(p.rival()),
				SeleccionImagenesRedes.hora(p.hora()),
				p.dia().format(DIA_MES),
				p.campo() == null ? "" : p.campo().trim().toUpperCase(Locale.ROOT),
				resultados ? SeleccionImagenesRedes.marcador(p) : null,
				SeleccionImagenesRedes.estado(p),
				escudo);
	}

	// ---- aviso a coordinación ----------------------------------------------------------------

	private void avisar(String tipo, LocalDate viernes) {

		Set<Long> destinatarios = new LinkedHashSet<>(equipoGestionDao.obtenerCoordinadores());
		destinatarios.addAll(equipoGestionDao.obtenerAdministradoresApp());

		String prefijo = TIPO_PREVIA.equals(tipo) ? "imagenesRedes.previa" : "imagenesRedes.resultados";

		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("tipo", PUSH_IMAGENES_REDES);
		datos.put("pantalla", PUSH_IMAGENES_REDES);
		datos.put("desde", viernes.toString());
		datos.put("tipoImagen", tipo);

		for (Long cuenta : destinatarios) {

			try {

				if (!notificacionAppService.puedeRecibir(cuenta, "COMUNICACION")) {
					continue;
				}

				String idioma = idiomas.idiomaDe(cuenta);
				String titulo = textos.texto(idioma, prefijo + ".titulo");
				String mensaje = textos.texto(idioma, prefijo + ".texto");

				notificacionAppService.crear(cuenta, TIPO_NOTIFICACION, titulo, mensaje, null);
				fcmPushService.enviarNotificacionAUsuario(cuenta, TIPO_NOTIFICACION, titulo, mensaje, null, datos);

			} catch (RuntimeException e) {
				logger.error("No se ha podido avisar a la cuenta {} de las imágenes para redes: {}", cuenta,
						e.getMessage(), e);
			}
		}
	}

	// ---- utilidades -------------------------------------------------------------------------------

	private ImagenesRedesResponse respuesta(LocalDate viernes) {

		LocalDate domingo = viernes.plusDays(2);

		List<ImagenesRedesResponse.Imagen> imagenes = new ArrayList<>();

		for (ImagenRedes i : imagenRedesDao.listar(viernes)) {
			imagenes.add(new ImagenesRedesResponse.Imagen(
					i.id(), i.tipo(), i.modalidad(), i.pagina(), i.totalPaginas(), i.version(), i.fechaGeneracion()));
		}

		return new ImagenesRedesResponse(viernes, domingo, imagenes, TextoPublicacionRedes.previa(viernes, domingo),
				TextoPublicacionRedes.resultados(viernes, domingo));
	}

	/** El viernes del fin de semana (mismo criterio que el panel Fin de semana de coordinación). */
	private LocalDate viernesDe(LocalDate fecha) {
		return CoordinacionServiceImpl.viernesDeReferencia(fecha != null ? fecha : LocalDate.now(clock));
	}

	private static String normalizarTipo(String tipo) {

		String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);

		if (!TIPO_PREVIA.equals(t) && !TIPO_RESULTADOS.equals(t)) {
			throw new IllegalArgumentException("El tipo debe ser PREVIA o RESULTADOS");
		}

		return t;
	}

	private void validarAcceso(Long usuarioAppId) {

		if (usuarioAppId == null || usuarioAppId <= 0 || !equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
			throw new SecurityException("Solo coordinación puede ver las imágenes para redes");
		}
	}

	/** Tras el commit si hay transacción (las imágenes ya están guardadas); si no, al momento. */
	private static void despuesDelCommit(Runnable tarea) {

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					tarea.run();
				}
			});
			return;
		}

		tarea.run();
	}
}
