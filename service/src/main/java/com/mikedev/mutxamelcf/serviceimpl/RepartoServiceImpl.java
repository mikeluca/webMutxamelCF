package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.EnumEquipos;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.JugadorRepartoDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo;
import com.mikedev.mutxamelcf.service.AlAsignarEquipoDefinitivo.AsignacionDefinitiva;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.RepartoService;

@Service
public class RepartoServiceImpl implements RepartoService {

	private static final Logger logger = LoggerFactory.getLogger(RepartoServiceImpl.class);

	private static final String PRETEMPORADA = "PRETEMPORADA";

	private final TemporadaDao temporadaDao;
	private final EquipoDao equipoDao;
	private final JugadorDao jugadorDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final CategoriaPorEdad categoriaPorEdad;
	private final EquipoGestionDao equipoGestionDao;
	private final List<AlAsignarEquipoDefinitivo> alAsignar;

	public RepartoServiceImpl(TemporadaDao temporadaDao, EquipoDao equipoDao, JugadorDao jugadorDao,
			JugadorTemporadaDao jugadorTemporadaDao, CategoriaPorEdad categoriaPorEdad,
			EquipoGestionDao equipoGestionDao, List<AlAsignarEquipoDefinitivo> alAsignar) {
		this.temporadaDao = temporadaDao;
		this.equipoDao = equipoDao;
		this.jugadorDao = jugadorDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.categoriaPorEdad = categoriaPorEdad;
		this.equipoGestionDao = equipoGestionDao;
		this.alAsignar = alAsignar;
	}

	// ============================================================
	// Acceso y utilidades
	// ============================================================

	@Override
	public void comprobarCoordinador(Long usuarioAppId) {
		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (!equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
			throw new SecurityException("Solo coordinación puede repartir jugadores");
		}
	}

	private Temporada activa() {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();

		if (activa == null) {
			throw new IllegalStateException("No hay ninguna temporada activa.");
		}

		return activa;
	}

	private static boolean mismaCategoria(String a, String b) {
		return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
	}

	/** La categoría con la grafía de CATEGORIAS_EDAD, o error si no es una categoría formativa. */
	private String categoriaFormativa(String categoria) {
		return categoriaPorEdad.obtenerTodas().stream().map(CategoriaEdad::getCategoria)
				.filter(c -> mismaCategoria(c, categoria)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("La categoría " + categoria
						+ " no es una categoría formativa: el reparto es solo para las categorías por edad."));
	}

	private static String nombre(JugadorTemporada fila) {
		return ((fila.getJugadorNombre() == null ? "" : fila.getJugadorNombre()) + " "
				+ (fila.getJugadorApellidos() == null ? "" : fila.getJugadorApellidos())).trim();
	}

	private static JugadorRepartoDTO aDto(JugadorTemporada f) {
		return new JugadorRepartoDTO(f.getJugadorId(), nombre(f), f.getAnioNacimiento(), f.getDorsal(), f.getEstado(),
				f.isEquipoDefinitivo());
	}

	private static EquipoRepartoDTO aDto(Equipo e, List<JugadorRepartoDTO> jugadores) {
		return new EquipoRepartoDTO(e.getId(), e.getNombre(), e.getGrupo(), e.getModalidad(),
				Boolean.TRUE.equals(e.getActivoTemporada()), jugadores);
	}

	// ============================================================
	// Consulta
	// ============================================================

	@Override
	@Transactional(readOnly = true)
	public List<String> categorias() {
		List<Equipo> equipos = equipoDao.obtenerTodos();

		return categoriaPorEdad.obtenerTodas().stream()
				.sorted(Comparator.comparing(CategoriaEdad::getOrden, Comparator.nullsLast(Integer::compareTo)))
				.map(CategoriaEdad::getCategoria)
				.filter(c -> equipos.stream().anyMatch(e -> e.getActivoTemporada() != null && mismaCategoria(e.getCategoria(), c)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public RepartoCategoriaDTO obtenerCategoria(String categoria) {
		String canonica = categoriaFormativa(categoria);
		Temporada activa = activa();

		// Equipos de la categoría que tienen fila en la temporada activa (activos o no: el reparto los activa)
		List<Equipo> equipos = equipoDao.obtenerTodos().stream()
				.filter(e -> e.getActivoTemporada() != null && mismaCategoria(e.getCategoria(), canonica)).toList();
		Equipo pretemporada = equipos.stream().filter(e -> PRETEMPORADA.equals(e.getTipoTemporada())).findFirst()
				.orElse(null);

		Map<Long, List<JugadorRepartoDTO>> porEquipo = new LinkedHashMap<>();
		List<JugadorRepartoDTO> porAsignar = new ArrayList<>();

		for (JugadorTemporada f : jugadorTemporadaDao.listarPorTemporada(activa.getId())) {
			if (f.isBaja() || !mismaCategoria(f.getCategoria(), canonica)) {
				continue;
			}

			if (f.isEquipoDefinitivo() && f.getEquipoId() != null) {
				porEquipo.computeIfAbsent(f.getEquipoId(), k -> new ArrayList<>()).add(aDto(f));
			} else if (!f.isEquipoDefinitivo()) {
				porAsignar.add(aDto(f));
			}
		}

		List<EquipoRepartoDTO> definitivos = equipos.stream().filter(e -> !PRETEMPORADA.equals(e.getTipoTemporada()))
				.sorted(Comparator.comparing(Equipo::getGrupo, Comparator.nullsLast(String::compareTo))
						.thenComparing(Equipo::getNombre, Comparator.nullsLast(String::compareTo)))
				.map(e -> aDto(e, porEquipo.getOrDefault(e.getId(), List.of()))).toList();

		return new RepartoCategoriaDTO(canonica, pretemporada == null ? null : aDto(pretemporada, List.of()),
				porAsignar, definitivos);
	}

	// ============================================================
	// Asignar
	// ============================================================

	@Override
	@Transactional
	public ResultadoRepartoDTO asignar(AsignacionRepartoRequest request) {
		if (request == null || request.equipoId() == null) {
			throw new IllegalArgumentException("Hay que indicar el equipo.");
		}
		if (request.jugadores() == null || request.jugadores().isEmpty()) {
			throw new IllegalArgumentException("Hay que indicar al menos un jugador.");
		}

		Temporada activa = activa();
		List<Equipo> equipos = equipoDao.obtenerTodos();
		Equipo equipo = equipos.stream().filter(e -> request.equipoId().equals(e.getId())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("El equipo no existe."));

		if (equipo.getActivoTemporada() == null || PRETEMPORADA.equals(equipo.getTipoTemporada())) {
			throw new IllegalArgumentException("El equipo no es un equipo definitivo de la temporada activa.");
		}

		String categoria = categoriaFormativa(equipo.getCategoria());

		Map<Long, JugadorTemporada> filas = new LinkedHashMap<>();
		jugadorTemporadaDao.listarPorTemporada(activa.getId()).forEach(f -> filas.put(f.getJugadorId(), f));

		// Validaciones: no se toca nada hasta que todo es válido
		Map<Long, Integer> dorsales = new LinkedHashMap<>();
		List<String> pruebas = new ArrayList<>();

		for (AsignacionRepartoRequest.Jugador j : request.jugadores()) {
			if (j == null || j.jugadorId() == null) {
				throw new IllegalArgumentException("Hay un jugador sin identificar.");
			}
			if (dorsales.containsKey(j.jugadorId())) {
				throw new IllegalArgumentException("El jugador " + j.jugadorId() + " está repetido en la lista.");
			}

			JugadorTemporada fila = filas.get(j.jugadorId());
			if (fila == null) {
				throw new IllegalArgumentException("El jugador " + j.jugadorId() + " no tiene ficha en la temporada activa.");
			}
			if (fila.isBaja()) {
				throw new IllegalArgumentException(nombre(fila) + " está de baja.");
			}
			if (!mismaCategoria(fila.getCategoria(), categoria)) {
				throw new IllegalArgumentException(nombre(fila) + " no es de la categoría " + categoria + ".");
			}
			if (j.dorsal() != null && (j.dorsal() < 0 || j.dorsal() > 99)) {
				throw new IllegalArgumentException("El dorsal debe estar entre 0 y 99 (" + nombre(fila) + ").");
			}

			if ("PRUEBA".equals(fila.getEstado())) {
				pruebas.add(nombre(fila));
			}

			dorsales.put(j.jugadorId(), j.dorsal());
		}

		if (!pruebas.isEmpty() && !request.confirmarPruebas()) {
			throw new PruebasSinConfirmarException(pruebas);
		}

		boolean activoAntes = Boolean.TRUE.equals(equipo.getActivoTemporada());
		Set<Long> equiposDeOrigen = new LinkedHashSet<>();
		List<AsignacionDefinitiva> eventos = new ArrayList<>();

		for (Map.Entry<Long, Integer> e : dorsales.entrySet()) {
			JugadorTemporada fila = filas.get(e.getKey());
			boolean eraPrueba = "PRUEBA".equals(fila.getEstado());

			if (fila.getEquipoId() != null && !fila.getEquipoId().equals(equipo.getId())) {
				equiposDeOrigen.add(fila.getEquipoId());
			}

			jugadorDao.asignarPlantilla(fila.getJugadorId(), equipo.getId(), equipo.getNombre(), equipo.getCategoria(),
					equipo.getDeporte(), e.getValue());

			fila.setEquipoId(equipo.getId());
			fila.setEquipoNombre(equipo.getNombre());
			fila.setCategoria(equipo.getCategoria());
			fila.setDorsal(e.getValue());
			fila.setEquipoDefinitivo(true);
			if (eraPrueba) {
				fila.setEstado("CONFIRMADO");
			}
			jugadorTemporadaDao.guardar(fila);

			eventos.add(new AsignacionDefinitiva(fila.getJugadorId(), equipo.getId(), activa.getId(), eraPrueba));
		}

		// El equipo entra en juego con su primer jugador
		if (!activoAntes) {
			equipoDao.marcarActivoEnTemporada(equipo.getId(), true);
		}

		// El equipo de pretemporada que se queda sin jugadores deja de estar activo
		boolean pretemporadaVaciada = false;
		for (Long origen : equiposDeOrigen) {
			Equipo anterior = equipos.stream().filter(eq -> origen.equals(eq.getId())).findFirst().orElse(null);

			if (anterior != null && PRETEMPORADA.equals(anterior.getTipoTemporada())
					&& Boolean.TRUE.equals(anterior.getActivoTemporada())
					&& jugadorTemporadaDao.contarEnEquipoEnLaTemporadaActiva(origen) == 0) {
				equipoDao.marcarActivoEnTemporada(origen, false);
				pretemporadaVaciada = true;
			}
		}

		List<String> avisos = avisosDeDorsal(equipo, filas.values(), dorsales.keySet());

		// Hook para lo que dependa del equipo definitivo (las cuotas, más adelante)
		eventos.forEach(ev -> alAsignar.forEach(h -> h.alAsignar(ev)));

		logger.info("Reparto: equipoId={}, asignados={}, equipoActivado={}, pretemporadaVaciada={}, avisos={}",
				equipo.getId(), eventos.size(), !activoAntes, pretemporadaVaciada, avisos.size());

		return new ResultadoRepartoDTO(eventos.size(), avisos, !activoAntes, pretemporadaVaciada, pruebas);
	}

	/** Dorsales repetidos en el equipo que afectan a alguno de los jugadores recién asignados. No impiden asignar. */
	private static List<String> avisosDeDorsal(Equipo equipo, Iterable<JugadorTemporada> filas, Set<Long> asignados) {
		Map<Integer, List<JugadorTemporada>> porDorsal = new TreeMap<>();

		for (JugadorTemporada f : filas) {
			if (equipo.getId().equals(f.getEquipoId()) && !f.isBaja() && f.getDorsal() != null) {
				porDorsal.computeIfAbsent(f.getDorsal(), k -> new ArrayList<>()).add(f);
			}
		}

		List<String> avisos = new ArrayList<>();

		porDorsal.forEach((dorsal, jugadores) -> {
			if (jugadores.size() > 1 && jugadores.stream().anyMatch(f -> asignados.contains(f.getJugadorId()))) {
				avisos.add("El dorsal " + dorsal + " está repetido en " + equipo.getNombre() + ": "
						+ jugadores.stream().map(RepartoServiceImpl::nombre).collect(Collectors.joining(", ")));
			}
		});

		return avisos;
	}

	// ============================================================
	// Crear equipo
	// ============================================================

	@Override
	@Transactional
	public EquipoRepartoDTO crearEquipo(String categoria) {
		String canonica = categoriaFormativa(categoria);
		activa();

		List<Equipo> delaCategoria = equipoDao.obtenerTodos().stream()
				.filter(e -> mismaCategoria(e.getCategoria(), canonica)).toList();

		// La siguiente letra a la mayor en uso (sin contar la P de la pretemporada)
		char mayor = 0;
		for (Equipo e : delaCategoria) {
			String grupo = e.getGrupo() == null ? "" : e.getGrupo().trim();

			if (grupo.length() == 1 && grupo.charAt(0) >= 'A' && grupo.charAt(0) <= 'Z' && grupo.charAt(0) != 'P') {
				mayor = (char) Math.max(mayor, grupo.charAt(0));
			}
		}

		char letra = mayor == 0 ? 'A' : (char) (mayor + 1);
		if (letra == 'P') {
			letra++;
		}
		if (letra > 'Z') {
			throw new IllegalArgumentException("No quedan letras libres para un equipo nuevo de " + canonica + ".");
		}

		Equipo modelo = delaCategoria.stream().filter(e -> !PRETEMPORADA.equals(e.getTipoTemporada())).findFirst()
				.orElse(null);
		Equipo pretemporada = delaCategoria.stream().filter(e -> PRETEMPORADA.equals(e.getTipoTemporada())).findFirst()
				.orElse(null);
		Equipo referencia = pretemporada != null ? pretemporada : modelo;

		Equipo nuevo = new Equipo();
		nuevo.setCategoria(canonica);
		nuevo.setGrupo(String.valueOf(letra));
		nuevo.setNombre(canonica + " " + letra);
		nuevo.setOrden(referencia != null && referencia.getOrden() != null ? referencia.getOrden()
				: EnumEquipos.getOrdenByCategoria(canonica));
		nuevo.setDeporte(referencia != null && referencia.getDeporte() != null ? referencia.getDeporte() : "F");
		nuevo.setModalidad(referencia != null ? referencia.getModalidad() : null);
		// Inactivo hasta que se le asigne el primer jugador
		nuevo.setActivoTemporada(false);

		if (!equipoDao.guardar(nuevo)) {
			throw new IllegalArgumentException("Ya existe un equipo " + nuevo.getNombre() + ".");
		}

		Equipo creado = equipoDao.obtenerEquipoPorNombre(nuevo.getNombre());
		logger.info("Equipo creado desde el reparto: {}", nuevo.getNombre());

		return aDto(creado, List.of());
	}
}
