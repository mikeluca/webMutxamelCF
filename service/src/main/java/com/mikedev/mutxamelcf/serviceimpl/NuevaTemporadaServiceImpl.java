package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AnioNacimiento;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado.Estado;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.ResumenAplicacionDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.Fila;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.GrupoCategoria;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.GrupoEquipo;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.SinPropuesta;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

@Service
public class NuevaTemporadaServiceImpl implements NuevaTemporadaService {

	private static final Logger logger = LoggerFactory.getLogger(NuevaTemporadaServiceImpl.class);

	static final String NOTA_SIN_ANIO = "Sin año de nacimiento";
	static final String NOTA_FIN_ETAPA = "Termina Juvenil: ¿Filial, Primer Equipo o baja?";
	static final String NOTA_PRUEBA = "Jugador de prueba";

	private static final Set<String> CONTINUIDADES = Set.of("PENDIENTE", "SIGUE", "NO_SIGUE", "DUDA");

	private final TemporadaDao temporadaDao;
	private final JugadorDao jugadorDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final EquipoDao equipoDao;
	private final CategoriaPorEdad categoriaPorEdad;
	private final Clock clock;

	public NuevaTemporadaServiceImpl(TemporadaDao temporadaDao, JugadorDao jugadorDao,
			JugadorTemporadaDao jugadorTemporadaDao, EquipoDao equipoDao, CategoriaPorEdad categoriaPorEdad,
			Clock clock) {
		this.temporadaDao = temporadaDao;
		this.jugadorDao = jugadorDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.equipoDao = equipoDao;
		this.categoriaPorEdad = categoriaPorEdad;
		this.clock = clock;
	}

	// ============================================================
	// Destino
	// ============================================================

	@Override
	public Temporada temporadaActiva() {
		return temporadaDao.obtenerTemporadaActiva();
	}

	@Override
	public List<Temporada> temporadasDestino() {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		if (activa == null) {
			return List.of();
		}

		return temporadaDao.obtenerTodos().stream().filter(t -> esDestinoValido(t, activa))
				.sorted(Comparator.comparing(Temporada::getFechaInicio)).toList();
	}

	private static boolean esDestinoValido(Temporada t, Temporada activa) {
		return t.getId() != null && !t.getId().equals(activa.getId()) && t.getFechaInicio() != null
				&& activa.getFechaInicio() != null && t.getFechaInicio().after(activa.getFechaInicio());
	}

	/** El destino tiene que existir, no estar activo y empezar después de la activa. */
	private Temporada validarDestino(Long destinoId) {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		if (activa == null) {
			throw new IllegalArgumentException("No hay ninguna temporada activa.");
		}

		Temporada destino = destinoId == null ? null : temporadaDao.obtenerPorId(destinoId);
		if (destino == null) {
			throw new IllegalArgumentException("La temporada de destino no existe.");
		}
		if (!esDestinoValido(destino, activa)) {
			throw new IllegalArgumentException(
					"La temporada de destino no puede estar activa y tiene que empezar después de la activa ("
							+ activa.getNombre() + ").");
		}

		return destino;
	}

	// ============================================================
	// Propuesta
	// ============================================================

	@Override
	@Transactional
	public int generarPropuesta(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		Contexto contexto = contexto(activa, destino);

		Set<Long> conFila = jugadorTemporadaDao.listarPorTemporada(destinoId).stream()
				.map(JugadorTemporada::getJugadorId).collect(Collectors.toSet());

		int creadas = 0;
		for (Jugador jugador : jugadorDao.obtenerActivos()) {
			if (!conFila.contains(jugador.getId())) {
				jugadorTemporadaDao.guardar(proponer(jugador, contexto));
				creadas++;
			}
		}

		logger.info("Propuesta generada: destino={}, filasCreadas={}", destino.getNombre(), creadas);
		return creadas;
	}

	/** Borra el borrador y lo vuelve a generar. Las altas de prueba se conservan: no salen de la plantilla actual. */
	@Override
	@Transactional
	public int regenerar(Long destinoId) {
		validarDestino(destinoId);
		int borradas = jugadorTemporadaDao.eliminarBorradorSinPrueba(destinoId);
		logger.info("Borrador borrado para regenerar (sin las pruebas): destinoId={}, filas={}", destinoId, borradas);
		return generarPropuesta(destinoId);
	}

	@Override
	@Transactional
	public void anadirSinPropuesta(Long destinoId, Long jugadorId) {
		Temporada destino = validarDestino(destinoId);
		Jugador jugador = jugadorDao.obtenerPorId(jugadorId);
		if (jugador == null || jugador.estaDeBaja()) {
			throw new IllegalArgumentException("Solo se pueden añadir jugadores activos.");
		}
		if (jugadorTemporadaDao.obtener(jugadorId, destinoId) != null) {
			throw new IllegalStateException("El jugador ya tiene fila en la temporada " + destino.getNombre() + ".");
		}

		jugadorTemporadaDao.guardar(proponer(jugador, contexto(temporadaDao.obtenerTemporadaActiva(), destino)));
	}

	/** Datos que comparten todas las propuestas de una generación. */
	private record Contexto(Temporada destino, int anioActiva, int anioDestino, Set<String> categoriasConEdad,
			Map<Long, Equipo> equiposPorId) {
	}

	private Contexto contexto(Temporada activa, Temporada destino) {
		Map<Long, Equipo> porId = new HashMap<>();
		equipoDao.obtenerTodos().forEach(e -> porId.put(e.getId(), e));

		// Las categorías que no están en CATEGORIAS_EDAD (Filial, Primer Equipo, Veteranos) no dependen de la edad.
		Set<String> conEdad = categoriaPorEdad.obtenerTodas().stream()
				.map(c -> c.getCategoria().trim().toLowerCase()).collect(Collectors.toSet());

		return new Contexto(destino, categoriaPorEdad.anioInicio(activa), categoriaPorEdad.anioInicio(destino), conEdad,
				porId);
	}

	/**
	 * Reglas de la propuesta para un jugador activo:
	 * <ol>
	 * <li>Equipo de una categoría que no está en CATEGORIAS_EDAD (Filial, Primer Equipo, Veteranos): mismo equipo y
	 * dorsal, equipo definitivo, continuidad SIGUE y sin revisión, tenga año de nacimiento o no. Va antes que todo lo
	 * demás.</li>
	 * <li>Categoría formativa: la que le toca por edad en el destino, sin equipo, sin dorsal y sin equipo definitivo
	 * (entrena en la pretemporada de su categoría hasta que el coordinador le asigne uno).</li>
	 * <li>Sin año de nacimiento: conserva su categoría actual, sin equipo, a revisar.</li>
	 * <li>Supera la edad máxima configurada (fin de Juvenil): sin equipo ni categoría, a revisar; decide el
	 * coordinador (Filial, Primer Equipo o baja).</li>
	 * <li>Ninguna categoría cubre su edad: mismo equipo y dorsal, a revisar.</li>
	 * <li>Hoy juega en una categoría distinta de la que le toca por edad: se propone según su edad y se marca a
	 * revisar, además de lo anterior.</li>
	 * </ol>
	 * Todas las filas nacen con CONTINUIDAD = PENDIENTE: hasta que no se resuelve (SIGUE) siguen pendientes de revisar.
	 */
	private JugadorTemporada proponer(Jugador jugador, Contexto contexto) {
		Equipo actual = jugador.getEquipoId() == null ? null : contexto.equiposPorId().get(jugador.getEquipoId());
		JugadorTemporada fila = new JugadorTemporada();
		fila.setJugadorId(jugador.getId());
		fila.setTemporadaId(contexto.destino().getId());
		fila.setEquipoDefinitivo(false);

		// Equipo de una categoría sin regla de edad (adultos): se queda como está, con o sin año de nacimiento.
		if (actual != null && actual.getCategoria() != null
				&& !contexto.categoriasConEdad().contains(actual.getCategoria().trim().toLowerCase())) {
			mismoEquipo(fila, actual, jugador);
			// Se quedan en su equipo (decisión del club): no esperan la respuesta de las familias
			fila.setContinuidad("SIGUE");
			return fila;
		}

		if (jugador.getAnioNacimiento() == null) {
			fila.setCategoria(actual != null ? actual.getCategoria() : jugador.getCategoria());
			fila.setPendienteRevision(true);
			fila.setNota(NOTA_SIN_ANIO);
			return fila;
		}

		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(jugador.getAnioNacimiento(),
				contexto.anioDestino());

		if (porEdad.estado() == Estado.SUPERA_EDAD_MAXIMA) {
			// Ya no se propone la baja: el coordinador decide (Filial, Primer Equipo o baja) en la revisión.
			fila.setPendienteRevision(true);
			fila.setNota(NOTA_FIN_ETAPA);
			return fila;
		}

		if (!porEdad.tieneCategoria()) {
			mismoEquipo(fila, actual, jugador);
			fila.setPendienteRevision(true);
			fila.setNota("Ninguna categoría cubre su edad (" + porEdad.edad() + " años)");
			return fila;
		}

		fila.setCategoria(porEdad.categoria());

		// Juega por arriba o por abajo: la categoría que le tocaba por edad esta temporada no es la de su equipo.
		if (actual != null) {
			CategoriaPorEdadResultado hoy = categoriaPorEdad.categoriaPara(jugador.getAnioNacimiento(),
					contexto.anioActiva());
			if (hoy.tieneCategoria() && !mismaCategoria(hoy.categoria(), actual.getCategoria())) {
				fila.setPendienteRevision(true);
				fila.setNota("Jugaba fuera de su categoría (" + actual.getCategoria() + ")");
			}
		}

		return fila;
	}

	private static void mismoEquipo(JugadorTemporada fila, Equipo actual, Jugador jugador) {
		if (actual != null) {
			copiarEquipo(fila, actual);
		}
		fila.setDorsal(jugador.getDorsal());
		fila.setEquipoDefinitivo(actual != null);
	}

	private static void copiarEquipo(JugadorTemporada fila, Equipo equipo) {
		fila.setEquipoId(equipo.getId());
		fila.setEquipoNombre(equipo.getNombre());
		fila.setCategoria(equipo.getCategoria());
	}

	private static boolean mismaCategoria(String a, String b) {
		return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
	}

	// ============================================================
	// Altas de prueba
	// ============================================================

	/**
	 * Crea al jugador y su fila PRUEBA en el borrador, con la categoría que le toca por edad y sin equipo.
	 *
	 * <p>
	 * Hasta que se aplique la temporada no existe en la temporada activa: su ficha nace con FECHA_BAJA (así ningún
	 * listado de jugadores activos la ve, porque todos filtran por FECHA_BAJA IS NULL), no tiene equipo ni fila en la
	 * temporada activa, y las bajas y el listado completo del panel la ocultan mientras tenga una fila PRUEBA. Al
	 * aplicar la temporada se activa en su categoría.
	 */
	@Override
	@Transactional
	public void anadirPrueba(Long destinoId, String nombre, String apellidos, Integer anioNacimiento) {
		Temporada destino = validarDestino(destinoId);

		String nombreLimpio = nombre == null ? "" : nombre.trim();
		String apellidosLimpios = apellidos == null ? "" : apellidos.trim();
		if (nombreLimpio.isEmpty() || apellidosLimpios.isEmpty()) {
			throw new IllegalArgumentException("El nombre y los apellidos son obligatorios.");
		}
		if (anioNacimiento == null) {
			throw new IllegalArgumentException("El año de nacimiento es obligatorio para un jugador de prueba.");
		}
		String errorAnio = AnioNacimiento.mensajeError(anioNacimiento, LocalDate.now(clock).getYear());
		if (errorAnio != null) {
			throw new IllegalArgumentException(errorAnio);
		}

		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(anioNacimiento,
				categoriaPorEdad.anioInicio(destino));
		if (porEdad.estado() == Estado.SUPERA_EDAD_MAXIMA || !porEdad.tieneCategoria()) {
			throw new IllegalArgumentException(
					"Ninguna categoría formativa cubre su edad (" + porEdad.edad() + " años) en " + destino.getNombre() + ".");
		}
		String categoria = porEdad.categoria();

		// El deporte lo da cualquier equipo de esa categoría; sin equipos, fútbol.
		String deporte = equipoDao.obtenerTodos().stream().filter(e -> mismaCategoria(e.getCategoria(), categoria))
				.map(Equipo::getDeporte).filter(d -> d != null && !d.isBlank()).findFirst().orElse("F");

		Jugador jugador = new Jugador();
		jugador.setNombre(nombreLimpio);
		jugador.setApellidos(apellidosLimpios);
		jugador.setCategoria(categoria);
		jugador.setDeporte(deporte);
		jugador.setAnioNacimiento(anioNacimiento);
		if (!jugadorDao.guardarJugador(jugador) || jugador.getId() == null) {
			throw new IllegalStateException("No se ha podido crear al jugador de prueba.");
		}
		jugadorDao.darDeBaja(jugador.getId(), LocalDate.now(clock));

		JugadorTemporada fila = new JugadorTemporada();
		fila.setJugadorId(jugador.getId());
		fila.setTemporadaId(destino.getId());
		fila.setCategoria(categoria);
		fila.setEstado("PRUEBA");
		// Una prueba no contesta la pregunta de continuidad: no se queda pendiente por eso.
		fila.setContinuidad("SIGUE");
		fila.setEquipoDefinitivo(false);
		fila.setNota(NOTA_PRUEBA);
		jugadorTemporadaDao.guardar(fila);

		logger.info("Alta de prueba: jugadorId={}, categoria={}, destino={}", jugador.getId(), categoria,
				destino.getNombre());
	}

	// ============================================================
	// Continuidad
	// ============================================================

	/**
	 * Marca la respuesta de continuidad de una fila del borrador. NO_SIGUE propone la baja, pero la fila queda
	 * pendiente hasta que el coordinador la confirme guardándola; PENDIENTE y DUDA dejan la fila pendiente de revisar.
	 */
	@Override
	@Transactional
	public void marcarContinuidad(Long destinoId, Long jugadorId, String continuidad) {
		validarDestino(destinoId);

		if (continuidad == null || !CONTINUIDADES.contains(continuidad)) {
			throw new IllegalArgumentException("La continuidad tiene que ser PENDIENTE, SIGUE, NO_SIGUE o DUDA.");
		}

		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugadorId, destinoId);
		if (fila == null) {
			throw new IllegalArgumentException("El jugador no tiene fila en esta temporada.");
		}
		if ("PRUEBA".equals(fila.getEstado())) {
			throw new IllegalArgumentException("Un jugador de prueba no tiene continuidad.");
		}

		boolean eraNoSigue = "NO_SIGUE".equals(fila.getContinuidad());
		fila.setContinuidad(continuidad);
		fila.setFechaContinuidad(LocalDateTime.now(clock));
		fila.setUsuarioAppContinuidadId(null);

		if ("NO_SIGUE".equals(continuidad)) {
			fila.setBaja(true);
			fila.setPendienteRevision(true);
		} else if (eraNoSigue) {
			// Dejó de ser una baja propuesta: que el coordinador lo revise.
			fila.setBaja(false);
			fila.setPendienteRevision(true);
		}

		jugadorTemporadaDao.guardar(fila);
	}

	// ============================================================
	// Revisión
	// ============================================================

	@Override
	public RevisionTemporadaDTO revision(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(destinoId);

		// Dorsales repetidos dentro de un mismo equipo destino (las bajas no cuentan).
		Map<String, Integer> usos = new HashMap<>();
		for (JugadorTemporada f : filas) {
			if (!f.isBaja() && f.getEquipoId() != null && f.getDorsal() != null) {
				usos.merge(f.getEquipoId() + ":" + f.getDorsal(), 1, Integer::sum);
			}
		}

		List<Fila> todas = new ArrayList<>();
		for (JugadorTemporada f : filas) {
			boolean repetido = !f.isBaja() && f.getEquipoId() != null && f.getDorsal() != null
					&& usos.get(f.getEquipoId() + ":" + f.getDorsal()) > 1;
			// Una prueba nueva tiene la ficha oculta (FECHA_BAJA), pero no está "de baja en el panel".
			boolean deBajaEnElPanel = f.isJugadorDeBaja() && !"PRUEBA".equals(f.getEstado());
			todas.add(new Fila(f.getJugadorId(), nombre(f.getJugadorNombre(), f.getJugadorApellidos()),
					f.getAnioNacimiento(), f.getEquipoActualId(), f.getEquipoActualNombre(), f.getEquipoId(),
					f.getEquipoNombre(), f.getCategoria(), f.getDorsal(), f.isBaja(), f.requiereRevision(), f.getNota(),
					repetido, deBajaEnElPanel, f.getEstado(), f.getContinuidad(), f.isEquipoDefinitivo()));
		}

		Map<Long, List<Fila>> porEquipo = new LinkedHashMap<>();
		Map<Long, String> nombres = new HashMap<>();
		Map<String, List<Fila>> porCategoria = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Fila f : todas) {
			if (f.baja()) {
				continue;
			}
			if (f.equipoId() != null) {
				porEquipo.computeIfAbsent(f.equipoId(), k -> new ArrayList<>()).add(f);
				nombres.put(f.equipoId(), f.equipoNombre());
			} else if (f.categoria() != null) {
				porCategoria.computeIfAbsent(f.categoria(), k -> new ArrayList<>()).add(f);
			}
		}

		List<GrupoEquipo> grupos = porEquipo.entrySet().stream()
				.map(e -> new GrupoEquipo(e.getKey(), nombres.get(e.getKey()), e.getValue().size(), e.getValue()))
				.sorted(Comparator.comparing(GrupoEquipo::equipoNombre, Comparator.nullsLast(String::compareTo)))
				.toList();

		List<GrupoCategoria> categorias = porCategoria.entrySet().stream()
				.map(e -> new GrupoCategoria(e.getKey(), e.getValue().size(), e.getValue())).toList();

		List<String> avisos = grupos.stream()
				.flatMap(g -> g.filas().stream().filter(Fila::dorsalRepetido)
						.map(f -> "Dorsal " + f.dorsal() + " repetido en " + g.equipoNombre()))
				.distinct().toList();

		List<SinPropuesta> sinPropuesta = jugadorTemporadaDao.jugadoresActivosSinFila(destinoId).stream()
				.map(j -> new SinPropuesta(j.getId(), nombre(j.getNombre(), j.getApellidos()), j.getAnioNacimiento(),
						j.getEquipo()))
				.toList();

		return new RevisionTemporadaDTO(destino, grupos, categorias, todas.stream().filter(Fila::pendiente).toList(),
				todas.stream().filter(f -> !f.baja() && f.equipoId() == null && f.categoria() == null).toList(),
				todas.stream().filter(Fila::baja).toList(), sinPropuesta, avisos, todas.size());
	}

	private static String nombre(String nombre, String apellidos) {
		return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
	}

	@Override
	@Transactional
	public void guardarFila(Long destinoId, Long jugadorId, Long equipoId, Integer dorsal, boolean baja,
			Integer anioNacimiento) {
		validarDestino(destinoId);

		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugadorId, destinoId);
		if (fila == null) {
			throw new IllegalArgumentException("El jugador no tiene fila en esta temporada.");
		}

		String errorAnio = AnioNacimiento.mensajeError(anioNacimiento, LocalDate.now(clock).getYear());
		if (errorAnio != null) {
			throw new IllegalArgumentException(errorAnio);
		}
		if (dorsal != null && (dorsal < 0 || dorsal > 99)) {
			throw new IllegalArgumentException("El dorsal debe estar entre 0 y 99.");
		}

		if (equipoId != null) {
			Equipo equipo = buscarEquipo(equipoId);
			if (equipo == null) {
				throw new IllegalArgumentException("El equipo seleccionado no existe.");
			}
			copiarEquipo(fila, equipo);
			fila.setEquipoDefinitivo(true);
			fila.setDorsal(dorsal);
		} else if (!baja) {
			// Sin equipo: entrena en su categoría, sin dorsal y sin equipo definitivo. Con el año de nacimiento se
			// recalcula la categoría (por si se acaba de rellenar).
			fila.setEquipoId(null);
			fila.setEquipoNombre(null);
			fila.setEquipoDefinitivo(false);
			fila.setDorsal(null);
			fila.setCategoria(categoriaSinEquipo(fila, anioNacimiento, destinoId));
		} else {
			fila.setDorsal(dorsal);
		}

		fila.setBaja(baja);
		fila.setPendienteRevision(false);
		jugadorTemporadaDao.guardar(fila);

		// El año de nacimiento se guarda en JUGADORES (null = no se conoce).
		jugadorDao.actualizarAnioNacimiento(jugadorId, anioNacimiento);
	}

	/** La categoría por edad si se conoce el año y una categoría formativa lo cubre; si no, la que ya tenía. */
	private String categoriaSinEquipo(JugadorTemporada fila, Integer anioNacimiento, Long destinoId) {
		if (anioNacimiento == null) {
			return fila.getCategoria();
		}

		Temporada destino = temporadaDao.obtenerPorId(destinoId);
		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(anioNacimiento,
				categoriaPorEdad.anioInicio(destino));

		return porEdad.estado() != Estado.SUPERA_EDAD_MAXIMA && porEdad.tieneCategoria() ? porEdad.categoria()
				: fila.getCategoria();
	}

	private Equipo buscarEquipo(Long equipoId) {
		try {
			return equipoDao.obtenerEquipoPorId(equipoId);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return null;
		}
	}

	// ============================================================
	// Aplicar
	// ============================================================

	@Override
	public ResumenAplicacionDTO resumenAplicacion(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(destinoId);

		List<String> bloqueos = new ArrayList<>();
		int sinFila = jugadorTemporadaDao.jugadoresActivosSinFila(destinoId).size();
		if (sinFila > 0) {
			bloqueos.add(sinFila + " jugador(es) activo(s) sin fila en " + destino.getNombre()
					+ ": añádelos desde \"Sin propuesta\".");
		}
		int pendientes = jugadorTemporadaDao.contarPendientes(destinoId);
		if (pendientes > 0) {
			bloqueos.add(pendientes + " fila(s) pendiente(s) de revisar (incluye la continuidad sin resolver).");
		}
		long sinAnio = jugadorDao.obtenerActivos().stream().filter(j -> j.getAnioNacimiento() == null).count();
		if (sinAnio > 0) {
			bloqueos.add(sinAnio + " jugador(es) activo(s) sin año de nacimiento: rellénalo en la revisión.");
		}

		Map<String, Integer> porEquipo = new LinkedHashMap<>();
		Map<String, Integer> porCategoria = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		int cambios = 0;
		int bajas = 0;
		int sinEquipo = 0;

		for (JugadorTemporada f : filas.stream().filter(f -> !f.isJugadorDeBaja() || "PRUEBA".equals(f.getEstado()))
				.toList()) {
			if (f.isBaja()) {
				bajas++;
			} else if (f.getEquipoId() != null) {
				porEquipo.merge(f.getEquipoNombre(), 1, Integer::sum);
				if (!f.getEquipoId().equals(f.getEquipoActualId())) {
					cambios++;
				}
			} else if (f.getCategoria() != null) {
				porCategoria.merge(f.getCategoria() + " (sin equipo)", 1, Integer::sum);
			} else {
				sinEquipo++;
			}
		}

		Map<String, Integer> ordenado = new LinkedHashMap<>();
		porEquipo.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> ordenado.put(e.getKey(), e.getValue()));
		ordenado.putAll(porCategoria);

		return new ResumenAplicacionDTO(destino.getNombre(), ordenado, cambios, bajas, sinEquipo, bloqueos);
	}

	@Override
	@Transactional
	public void aplicar(Long destinoId, String confirmacion) {
		Temporada destino = validarDestino(destinoId);

		if (confirmacion == null || !destino.getNombre().trim().equals(confirmacion.trim())) {
			throw new IllegalArgumentException("Para confirmar, escribe el nombre de la temporada: " + destino.getNombre());
		}

		ResumenAplicacionDTO resumen = resumenAplicacion(destinoId);
		if (!resumen.puedeAplicar()) {
			throw new IllegalStateException("No se puede aplicar: " + String.join(" ", resumen.bloqueos()));
		}

		LocalDate hoy = LocalDate.now(clock);
		Set<Long> yaDeBaja = new HashSet<>();

		for (JugadorTemporada fila : jugadorTemporadaDao.listarPorTemporada(destinoId)) {
			if ("PRUEBA".equals(fila.getEstado())) {
				// La prueba sale de su ficha oculta: pasa a ser un jugador activo de su categoría, sin equipo.
				jugadorDao.activarSinEquipo(fila.getJugadorId(), fila.getCategoria());
				continue;
			}

			if (fila.isJugadorDeBaja()) {
				// Se dio de baja desde el panel mientras se preparaba el borrador: sigue de baja.
				yaDeBaja.add(fila.getJugadorId());
				continue;
			}

			if (fila.isBaja()) {
				jugadorDao.darDeBaja(fila.getJugadorId(), hoy);
			} else if (fila.getEquipoId() == null) {
				// Categoría por edad, sin equipo y sin dorsal
				jugadorDao.asignarPlantilla(fila.getJugadorId(), null, null, fila.getCategoria(), null, fila.getDorsal());
			} else {
				Equipo equipo = equipoDao.obtenerEquipoPorId(fila.getEquipoId());
				jugadorDao.asignarPlantilla(fila.getJugadorId(), equipo.getId(), equipo.getNombre(),
						equipo.getCategoria(), equipo.getDeporte(), fila.getDorsal());
			}
		}

		temporadaDao.desactivarOtrasTemporadas(destinoId);
		if (!temporadaDao.activar(destinoId)) {
			throw new IllegalStateException("No se ha podido activar la temporada " + destino.getNombre() + ".");
		}

		logger.info("Temporada aplicada y activada: destino={}, bajas={}, sinEquipo={}, cambiosDeEquipo={}, yaDeBaja={}",
				destino.getNombre(), resumen.bajas(), resumen.sinEquipo(), resumen.cambiosDeEquipo(), yaDeBaja.size());
	}
}
