package com.mikedev.mutxamelcf.mvc.controller;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.BecadoService;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

@Controller
@RequestMapping("/admin/pagos")
public class PagosController {

    private static final Logger logger = LoggerFactory.getLogger(PagosController.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final TemporadaService temporadaService;
    private final ConceptoPagoService conceptoPagoService;
    private final CuotaJugadorService cuotaJugadorService;
    private final PagoService pagoService;
    private final BecadoService becadoService;
    private final JugadorService jugadorService;
    private final EquipoService equipoService;

    public PagosController(TemporadaService temporadaService, ConceptoPagoService conceptoPagoService,
            CuotaJugadorService cuotaJugadorService, PagoService pagoService, BecadoService becadoService,
            JugadorService jugadorService, EquipoService equipoService) {
        this.temporadaService = temporadaService;
        this.conceptoPagoService = conceptoPagoService;
        this.cuotaJugadorService = cuotaJugadorService;
        this.pagoService = pagoService;
        this.becadoService = becadoService;
        this.jugadorService = jugadorService;
        this.equipoService = equipoService;
    }

    @GetMapping
    public String dashboard(@RequestParam(required = false) Long temporadaId, Model model) {
        logger.debug("Inicio dashboard: temporadaId={}", temporadaId);
        List<TemporadaDTO> temporadas = temporadaService.obtenerTodos();
        TemporadaDTO temporada = resolverTemporada(temporadaId, temporadas);

        List<ConceptoPagoDTO> conceptos = temporada == null ? List.of()
                : conceptoPagoService.obtenerPorTemporada(temporada.getId());
        List<CuotaJugadorDTO> cuotas = temporada == null ? List.of()
                : cuotaJugadorService.obtenerPorTemporada(temporada.getId());
        Map<Long, JugadorDTO> jugadoresPorId = indexarPorId(jugadorService.obtenerTodos(), JugadorDTO::getId);
        Map<Long, ConceptoPagoDTO> conceptosPorId = indexarPorId(conceptos, ConceptoPagoDTO::getId);

        ResumenCuotas resumen = construirResumenCuotas(cuotas, conceptosPorId, jugadoresPorId);
        Map<String, Map<String, Object>> resumenPorEquipo = construirResumenPorEquipo(
                resumen.resumenPorJugador.values());

        model.addAttribute("temporadas", temporadas);
        model.addAttribute("temporadaSeleccionada", temporada);
        model.addAttribute("conceptos", conceptos);
        model.addAttribute("cuotas", resumen.filas);
        model.addAttribute("resumenJugadores", resumen.resumenPorJugador.values());
        model.addAttribute("resumenEquipos", resumenPorEquipo.values());
        model.addAttribute("totalPrevisto", resumen.totalPrevisto);
        model.addAttribute("totalPagado", resumen.totalPagado);
        model.addAttribute("totalPendiente", resumen.totalPrevisto.subtract(resumen.totalPagado).max(BigDecimal.ZERO));
        model.addAttribute("cuotasPendientes", resumen.cuotasPendientes);
        model.addAttribute("fechaHoy", new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        logger.debug("Fin dashboard: filas={}, jugadores={}, equipos={}", resumen.filas.size(),
                resumen.resumenPorJugador.size(), resumenPorEquipo.size());
        return "admin/pagos";
    }

    // Resuelve la temporada seleccionada: la indicada por id, si no la activa, si
    // no la primera disponible
    private TemporadaDTO resolverTemporada(Long temporadaId, List<TemporadaDTO> temporadas) {
        TemporadaDTO temporada = temporadaId == null ? temporadaService.obtenerTemporadaActiva()
                : temporadaService.obtenerPorId(temporadaId);
        if (temporada == null && !temporadas.isEmpty()) {
            temporada = temporadas.get(0);
        }
        return temporada;
    }

    // Indexa una lista de elementos por su identificador para accesos O(1)
    // posteriores
    private <T> Map<Long, T> indexarPorId(List<T> elementos, Function<T, Long> idExtractor) {
        Map<Long, T> indice = new HashMap<>();
        elementos.forEach(elemento -> indice.put(idExtractor.apply(elemento), elemento));
        return indice;
    }

    // Construye la fila de una cuota con sus datos calculados: pagado, pendiente,
    // estado y pagos asociados. Los pagos ya vienen resueltos (una sola consulta
    // para todas las cuotas, ver construirResumenCuotas) en vez de consultarse
    // aqui uno a uno por cuota
    private Map<String, Object> construirFila(CuotaJugadorDTO cuota, ConceptoPagoDTO concepto, JugadorDTO jugador,
            List<PagoDTO> pagosDeLaCuota) {
        BigDecimal pagado = pagosDeLaCuota.stream()
                .map(pago -> zeroIfNull(pago.getImporte()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal importe = zeroIfNull(cuota.getImporte());
        BigDecimal pendiente = importe.subtract(pagado).max(BigDecimal.ZERO);

        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("cuota", cuota);
        fila.put("periodoTexto", formatearPeriodo(cuota.getPeriodo()));
        fila.put("concepto", concepto);
        fila.put("jugador", jugador);
        fila.put("pagado", pagado);
        fila.put("pendiente", pendiente);
        fila.put("estadoCalculado", pendiente.signum() == 0 ? "PAGADO" : pagado.signum() > 0 ? "PARCIAL" : "PENDIENTE");
        fila.put("pagos", pagosDeLaCuota);
        return fila;
    }

    // Acumula una fila de cuota en el resumen agregado del jugador correspondiente,
    // creandolo si es necesario
    private void acumularEnResumenJugador(Map<Long, Map<String, Object>> resumenPorJugador, Map<String, Object> fila,
            Long jugadorId, JugadorDTO jugador, BigDecimal importe, BigDecimal pagado) {
        Map<String, Object> resumen = resumenPorJugador.computeIfAbsent(jugadorId, id -> {
            Map<String, Object> nuevo = new LinkedHashMap<>();
            nuevo.put("jugador", jugador);
            nuevo.put("modalId", "jugadorDialog-" + jugadorId);
            nuevo.put("cuotas", new ArrayList<Map<String, Object>>());
            nuevo.put("totalPrevisto", BigDecimal.ZERO);
            nuevo.put("totalPagado", BigDecimal.ZERO);
            return nuevo;
        });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cuotasDelJugador = (List<Map<String, Object>>) resumen.get("cuotas");
        cuotasDelJugador.add(fila);
        resumen.put("totalPrevisto", ((BigDecimal) resumen.get("totalPrevisto")).add(importe));
        resumen.put("totalPagado", ((BigDecimal) resumen.get("totalPagado")).add(pagado));
    }

    // Recorre las cuotas de la temporada construyendo las filas y el resumen
    // agregado por jugador
    private ResumenCuotas construirResumenCuotas(List<CuotaJugadorDTO> cuotas,
            Map<Long, ConceptoPagoDTO> conceptosPorId,
            Map<Long, JugadorDTO> jugadoresPorId) {
        Map<Long, List<PagoDTO>> pagosPorCuota = obtenerPagosPorCuota(cuotas);
        ResumenCuotas resultado = new ResumenCuotas();
        for (CuotaJugadorDTO cuota : cuotas) {
            ConceptoPagoDTO concepto = conceptosPorId.get(cuota.getConceptoPagoId());
            if (concepto == null) {
                continue;
            }
            JugadorDTO jugador = jugadoresPorId.get(cuota.getJugadorId());
            List<PagoDTO> pagosDeLaCuota = pagosPorCuota.getOrDefault(cuota.getId(), List.of());
            Map<String, Object> fila = construirFila(cuota, concepto, jugador, pagosDeLaCuota);
            BigDecimal importe = zeroIfNull(cuota.getImporte());
            BigDecimal pagado = (BigDecimal) fila.get("pagado");
            BigDecimal pendiente = (BigDecimal) fila.get("pendiente");

            resultado.filas.add(fila);
            resultado.totalPrevisto = resultado.totalPrevisto.add(importe);
            resultado.totalPagado = resultado.totalPagado.add(pagado);
            if (pendiente.signum() > 0) {
                resultado.cuotasPendientes++;
            }
            acumularEnResumenJugador(resultado.resumenPorJugador, fila, cuota.getJugadorId(), jugador, importe, pagado);
        }
        for (Map<String, Object> resumen : resultado.resumenPorJugador.values()) {
            BigDecimal previsto = (BigDecimal) resumen.get("totalPrevisto");
            BigDecimal pagado = (BigDecimal) resumen.get("totalPagado");
            resumen.put("totalPendiente", previsto.subtract(pagado).max(BigDecimal.ZERO));
        }
        return resultado;
    }

    // Trae de una sola vez los pagos de todas las cuotas recibidas y los agrupa
    // por cuota, evitando una consulta a PAGOS por cada cuota
    private Map<Long, List<PagoDTO>> obtenerPagosPorCuota(List<CuotaJugadorDTO> cuotas) {
        List<Long> cuotaIds = cuotas.stream().map(CuotaJugadorDTO::getId).toList();
        return pagoService.obtenerPorCuotas(cuotaIds).stream()
                .collect(Collectors.groupingBy(PagoDTO::getCuotaJugadorId, LinkedHashMap::new, Collectors.toList()));
    }

    // Agrupa los resumenes por jugador en resumenes por equipo (o "Sin equipo" si
    // el jugador no tiene equipo asignado)
    private Map<String, Map<String, Object>> construirResumenPorEquipo(
            Collection<Map<String, Object>> resumenesJugadores) {
        Map<String, Map<String, Object>> resumenPorEquipo = new LinkedHashMap<>();
        for (Map<String, Object> resumen : resumenesJugadores) {
            JugadorDTO jugador = (JugadorDTO) resumen.get("jugador");
            String equipo = jugador == null || jugador.getEquipo() == null || jugador.getEquipo().isBlank()
                    ? "Sin equipo"
                    : jugador.getEquipo();
            Map<String, Object> equipoResumen = resumenPorEquipo.computeIfAbsent(equipo, nombre -> {
                Map<String, Object> nuevo = new LinkedHashMap<>();
                nuevo.put("nombre", nombre);
                nuevo.put("jugadores", new ArrayList<Map<String, Object>>());
                nuevo.put("totalPrevisto", BigDecimal.ZERO);
                nuevo.put("totalPagado", BigDecimal.ZERO);
                return nuevo;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> jugadoresDelEquipo = (List<Map<String, Object>>) equipoResumen.get("jugadores");
            jugadoresDelEquipo.add(resumen);
            equipoResumen.put("totalPrevisto", ((BigDecimal) equipoResumen.get("totalPrevisto"))
                    .add((BigDecimal) resumen.get("totalPrevisto")));
            equipoResumen.put("totalPagado", ((BigDecimal) equipoResumen.get("totalPagado"))
                    .add((BigDecimal) resumen.get("totalPagado")));
        }
        for (Map<String, Object> equipo : resumenPorEquipo.values()) {
            BigDecimal previsto = (BigDecimal) equipo.get("totalPrevisto");
            BigDecimal pagado = (BigDecimal) equipo.get("totalPagado");
            equipo.put("totalPendiente", previsto.subtract(pagado).max(BigDecimal.ZERO));
        }
        return resumenPorEquipo;
    }

    // Contenedor de los resultados intermedios calculados por
    // construirResumenCuotas
    private static final class ResumenCuotas {
        private final List<Map<String, Object>> filas = new ArrayList<>();
        private final Map<Long, Map<String, Object>> resumenPorJugador = new LinkedHashMap<>();
        private BigDecimal totalPrevisto = BigDecimal.ZERO;
        private BigDecimal totalPagado = BigDecimal.ZERO;
        private long cuotasPendientes;
    }

    @GetMapping("/temporadas")
    public String temporadas(Model model) {
        logger.debug("Inicio temporadas");
        List<TemporadaDTO> temporadas = temporadaService.obtenerTodos();
        model.addAttribute("temporadas", temporadas);
        logger.debug("Fin temporadas: total={}", temporadas.size());
        return "admin/pagos-temporadas";
    }

    @GetMapping("/conceptos")
    public String conceptos(@RequestParam(required = false) Long temporadaId, Model model) {
        logger.debug("Inicio conceptos: temporadaId={}", temporadaId);
        List<TemporadaDTO> temporadas = temporadaService.obtenerTodos();
        TemporadaDTO seleccionada = resolverTemporada(temporadaId, temporadas);
        model.addAttribute("temporadas", temporadas);
        model.addAttribute("temporadaSeleccionada", seleccionada);
        String[] periodosTemporada = obtenerPeriodosTemporada(seleccionada);
        model.addAttribute("periodoInicioTemporada", periodosTemporada[0]);
        model.addAttribute("periodoFinTemporada", periodosTemporada[1]);
        model.addAttribute("conceptos", seleccionada == null ? List.of()
                : conceptoPagoService.obtenerPorTemporada(seleccionada.getId()));
        logger.debug("Fin conceptos: temporadaId={}", temporadaId);
        return "admin/pagos-conceptos";
    }

    @GetMapping("/asignar-cuotas")
    public String asignarCuotas(@RequestParam(required = false) Long temporadaId,
            @RequestParam(required = false) String equipo, Model model) {
        logger.debug("Inicio asignarCuotas: temporadaId={}, equipo={}", temporadaId, equipo);
        List<TemporadaDTO> temporadas = temporadaService.obtenerTodos();
        TemporadaDTO seleccionada = resolverTemporada(temporadaId, temporadas);
        Long temporadaSeleccionadaId = seleccionada == null ? null : seleccionada.getId();
        List<JugadorDTO> todosLosJugadores = jugadorService.obtenerTodos();
        List<JugadorDTO> jugadoresFiltrados = equipo == null || equipo.isBlank() ? todosLosJugadores
                : todosLosJugadores.stream().filter(jugador -> equipo.equals(jugador.getEquipo())).toList();

        model.addAttribute("temporadas", temporadas);
        model.addAttribute("temporadaSeleccionada", seleccionada);
        model.addAttribute("conceptos", seleccionada == null ? List.of()
                : conceptoPagoService.obtenerActivosPorTemporada(seleccionada.getId()));
        model.addAttribute("conceptosPorId", indexarPorId(conceptoPagoService.obtenerTodos(), ConceptoPagoDTO::getId));
        model.addAttribute("categorias", equipoService.obtenerCategorias());
        model.addAttribute("equipos", obtenerNombresEquipos(todosLosJugadores));
        model.addAttribute("equipoSeleccionado", equipo);
        List<CuotaJugadorDTO> cuotasAsignadas = obtenerCuotasAsignadas(temporadaSeleccionadaId, equipo,
                todosLosJugadores);
        model.addAttribute("cuotasAsignadas", cuotasAsignadas);
        model.addAttribute("cuotasPorJugador", agruparCuotasPorJugador(cuotasAsignadas,
                indexarPorId(todosLosJugadores, JugadorDTO::getId)));
        model.addAttribute("jugadores", indexarPorId(todosLosJugadores, JugadorDTO::getId));
        model.addAttribute("jugadoresDisponibles", jugadoresFiltrados);

        logger.debug("Fin asignarCuotas: temporadaId={}, jugadores={}", temporadaSeleccionadaId,
                todosLosJugadores.size());
        return "admin/pagos-asignar";
    }

    @GetMapping("/asignar-cuota-jugador")
    public String asignarCuotaJugador(@RequestParam(required = false) Long temporadaId,
            @RequestParam(required = false) String equipo) {
        logger.debug("Inicio asignarCuotaJugador (redirigido): temporadaId={}, equipo={}", temporadaId, equipo);
        StringBuilder redirect = new StringBuilder("/admin/pagos/asignar-cuotas");
        if (temporadaId != null) {
            redirect.append("?temporadaId=").append(temporadaId);
            if (equipo != null && !equipo.isBlank()) {
                redirect.append("&equipo=").append(equipo);
            }
        }
        logger.debug("Fin asignarCuotaJugador (redirigido): destino={}", redirect);
        return "redirect:" + redirect;
    }

    // Extrae los nombres de equipo distintos y ordenados por la propiedad Orden del
    // equipo
    private List<String> obtenerNombresEquipos(List<JugadorDTO> jugadores) {
        Map<String, String> ordenPorEquipo = equipoService.obtenerTodos().stream()
                .filter(equipo -> equipo.getNombre() != null && !equipo.getNombre().isBlank())
                .collect(Collectors.toMap(
                        EquipoDTO::getNombre,
                        equipo -> equipo.getOrden() == null ? "" : equipo.getOrden(),
                        (ordenActual, siguiente) -> ordenActual,
                        LinkedHashMap::new));

        return jugadores.stream().map(JugadorDTO::getEquipo)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .sorted(Comparator.comparing((String nombre) -> ordenPorEquipo.getOrDefault(nombre, ""),
                        Comparator.nullsLast(String::compareTo))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    // Obtiene las cuotas ya asignadas en la temporada indicada, opcionalmente
    // filtradas por equipo
    private List<CuotaJugadorDTO> obtenerCuotasAsignadas(Long temporadaId, String equipo,
            List<JugadorDTO> todosLosJugadores) {
        if (temporadaId == null) {
            return List.of();
        }
        List<ConceptoPagoDTO> conceptosDeTemporada = conceptoPagoService.obtenerPorTemporada(temporadaId);
        return cuotaJugadorService.obtenerTodos().stream()
                .filter(cuota -> conceptosDeTemporada.stream()
                        .anyMatch(concepto -> concepto.getId().equals(cuota.getConceptoPagoId())))
                .filter(cuota -> equipo == null || equipo.isBlank()
                        || todosLosJugadores.stream().anyMatch(jugador -> jugador.getId().equals(cuota.getJugadorId())
                                && equipo.equals(jugador.getEquipo())))
                .toList();
    }

    private List<Map<String, Object>> agruparCuotasPorJugador(List<CuotaJugadorDTO> cuotas,
            Map<Long, JugadorDTO> jugadoresPorId) {
        Map<Long, Map<String, Object>> agrupadas = new LinkedHashMap<>();
        cuotas.stream()
                .sorted(Comparator.comparing(CuotaJugadorDTO::getJugadorId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(CuotaJugadorDTO::getPeriodo, Comparator.nullsLast(String::compareTo)))
                .forEach(cuota -> {
                    Map<String, Object> jugador = agrupadas.computeIfAbsent(cuota.getJugadorId(), id -> {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("jugador", jugadoresPorId.get(id));
                        fila.put("cuotas", new ArrayList<CuotaJugadorDTO>());
                        return fila;
                    });
                    @SuppressWarnings("unchecked")
                    List<CuotaJugadorDTO> cuotasJugador = (List<CuotaJugadorDTO>) jugador.get("cuotas");
                    cuotasJugador.add(cuota);
                });
        return new ArrayList<>(agrupadas.values());
    }

    @PostMapping("/temporadas/guardar")
    public String guardarTemporada(@RequestParam(required = false) Long id, @RequestParam String nombre,
            @RequestParam String fechaInicio, @RequestParam(required = false) String fechaFin,
            @RequestParam(defaultValue = "0") Integer activa, RedirectAttributes redirect) {
        logger.debug("Inicio guardarTemporada: id={}, nombre={}", id, nombre);
        TemporadaDTO temporada = new TemporadaDTO();
        temporada.setId(id);
        temporada.setNombre(nombre);
        temporada.setFechaInicio(parseDate(fechaInicio));
        temporada.setFechaFin(parseDate(fechaFin));
        temporada.setActiva(activa);
        temporadaService.guardarTemporada(temporada);
        redirect.addFlashAttribute("mensaje", "Temporada guardada correctamente.");
        logger.debug("Fin guardarTemporada: id={}", id);
        return "redirect:/admin/pagos/temporadas";
    }

    @PostMapping("/temporadas/borrar")
    public String borrarTemporada(@RequestParam Long id, RedirectAttributes redirect) {
        logger.debug("Inicio borrarTemporada: id={}", id);
        temporadaService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Temporada eliminada correctamente.");
        logger.debug("Fin borrarTemporada: id={}", id);
        return "redirect:/admin/pagos/temporadas";
    }

    @PostMapping("/conceptos/guardar")
    public String guardarConcepto(@RequestParam(required = false) Long id, @RequestParam Long temporadaId,
            @RequestParam String nombre, @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal importe, @RequestParam(required = false) Integer activo,
            RedirectAttributes redirect) {
        logger.debug("Inicio guardarConcepto: id={}, temporadaId={}, nombre={}", id, temporadaId, nombre);
        ConceptoPagoDTO concepto = new ConceptoPagoDTO();
        concepto.setId(id);
        concepto.setTemporadaId(temporadaId);
        concepto.setNombre(nombre);
        concepto.setDescripcion(descripcion);
        concepto.setImporte(importe);
        concepto.setActivo(activo == null ? 0 : activo);
        conceptoPagoService.guardarConceptoPago(concepto);
        redirect.addAttribute("temporadaId", temporadaId);
        redirect.addFlashAttribute("mensaje", "Cuota de temporada guardada correctamente.");
        logger.debug("Fin guardarConcepto: id={}, temporadaId={}", id, temporadaId);
        return "redirect:/admin/pagos/conceptos?temporadaId=" + temporadaId;
    }

    @PostMapping("/conceptos/borrar")
    public String borrarConcepto(@RequestParam Long id, @RequestParam Long temporadaId, RedirectAttributes redirect) {
        logger.debug("Inicio borrarConcepto: id={}, temporadaId={}", id, temporadaId);
        conceptoPagoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Concepto eliminado correctamente.");
        logger.debug("Fin borrarConcepto: id={}, temporadaId={}", id, temporadaId);
        return "redirect:/admin/pagos/conceptos?temporadaId=" + temporadaId;
    }

    @PostMapping("/cuotas/guardar")
    public String guardarCuota(@RequestParam Long conceptoPagoId, @RequestParam String categoria,
            @RequestParam(required = false) String equipo, @RequestParam Long temporadaId,
            @RequestParam(required = false) String periodoInicio, @RequestParam(required = false) String periodoFin,
            @RequestParam(required = false) String fechaLimite, @RequestParam(required = false) String observaciones,
            RedirectAttributes redirect) {
        logger.debug("Inicio guardarCuota: conceptoPagoId={}, categoria={}, equipo={}", conceptoPagoId, categoria,
                equipo);
        ConceptoPagoDTO concepto = conceptoPagoService.obtenerPorId(conceptoPagoId);
        if (concepto == null || concepto.getImporte() == null || !temporadaId.equals(concepto.getTemporadaId())) {
            logger.warn("Concepto de pago invalido para asignar cuotas: conceptoPagoId={}, temporadaId={}",
                    conceptoPagoId,
                    temporadaId);
            redirect.addFlashAttribute("error", "El concepto de pago no existe o no tiene importe.");
            logger.debug("Fin guardarCuota: resultado=CONCEPTO_INVALIDO");
            return redirectAsignarCategoria(temporadaId, equipo);
        }
        String[] periodosTemporada = obtenerPeriodosTemporada(temporadaService.obtenerPorId(temporadaId));
        List<String> periodosValidos = esCuotaMensual(concepto)
                ? generarPeriodos(periodosTemporada[0], periodosTemporada[1])
                : java.util.Collections.singletonList(null);
        if (esCuotaMensual(concepto) && periodosValidos.isEmpty()) {
            redirect.addFlashAttribute("error", "Selecciona al menos un mes para asignar la cuota.");
            return redirectAsignarCategoria(temporadaId, equipo);
        }
        List<JugadorDTO> jugadores = jugadorService.obtenerJugadoresPorCategoria(categoria);
        if (equipo != null && !equipo.isBlank()) {
            jugadores = jugadores.stream().filter(jugador -> equipo.equals(jugador.getEquipo())).toList();
        }
        int cuotasCreadas = 0;
        for (JugadorDTO jugador : jugadores) {
            for (String periodo : periodosValidos) {
                if (existeCuotaAsignada(jugador.getId(), conceptoPagoId, periodo)) {
                    continue;
                }
                CuotaJugadorDTO cuota = crearCuota(jugador.getId(), conceptoPagoId, concepto.getImporte(), periodo,
                        fechaLimite, observaciones);
                if (cuotaJugadorService.guardarCuota(cuota)) {
                    cuotasCreadas++;
                }
            }
        }
        redirect.addFlashAttribute("mensaje", "Se crearon " + cuotasCreadas + " cuotas mensuales.");
        logger.debug("Fin guardarCuota: cuotasCreadas={}", cuotasCreadas);
        return redirectAsignarCategoria(temporadaId, equipo);
    }

    @PostMapping("/cuotas/guardar-jugador")
    public String guardarCuotaJugador(@RequestParam Long jugadorId, @RequestParam Long temporadaId,
            @RequestParam Long conceptoPagoId, @RequestParam(required = false) String periodoInicio,
            @RequestParam(required = false) String periodoFin,
            @RequestParam(required = false) String equipo, RedirectAttributes redirect) {
        logger.debug("Inicio guardarCuotaJugador: jugadorId={}, conceptoPagoId={}", jugadorId, conceptoPagoId);
        ConceptoPagoDTO concepto = conceptoPagoService.obtenerPorId(conceptoPagoId);
        boolean conceptoActivo = concepto != null
                && conceptoPagoService.obtenerActivosPorTemporada(temporadaId).stream()
                        .anyMatch(activo -> activo.getId().equals(conceptoPagoId));
        if (concepto == null || concepto.getImporte() == null || !temporadaId.equals(concepto.getTemporadaId())
                || !conceptoActivo || jugadorService.obtenerJugadorPorId(jugadorId) == null) {
            logger.warn("Jugador o concepto de pago invalido: jugadorId={}, conceptoPagoId={}", jugadorId,
                    conceptoPagoId);
            redirect.addFlashAttribute("error", "El jugador o el concepto de pago no existe.");
            logger.debug("Fin guardarCuotaJugador: resultado=INVALIDO");
            return redirectAsignarCategoria(temporadaId, equipo);
        }
        String[] periodosTemporada = obtenerPeriodosTemporada(temporadaService.obtenerPorId(temporadaId));
        List<String> periodos = esCuotaMensual(concepto) ? generarPeriodos(periodosTemporada[0], periodosTemporada[1])
                : java.util.Collections.singletonList(null);
        if (esCuotaMensual(concepto) && periodos.isEmpty()) {
            redirect.addFlashAttribute("error", "Selecciona un rango de meses válido.");
            return redirectAsignarCategoria(temporadaId, equipo);
        }
        int cuotasCreadas = 0;
        for (String periodo : periodos) {
            if (existeCuotaAsignada(jugadorId, conceptoPagoId, periodo)) {
                continue;
            }
            CuotaJugadorDTO cuota = crearCuota(jugadorId, conceptoPagoId, concepto.getImporte(), periodo, null, null);
            if (cuotaJugadorService.guardarCuota(cuota)) {
                cuotasCreadas++;
            }
        }
        redirect.addFlashAttribute("mensaje", "Se crearon " + cuotasCreadas + " cuotas mensuales.");
        logger.debug("Fin guardarCuotaJugador: jugadorId={}, cuotasCreadas={}", jugadorId, cuotasCreadas);
        return redirectAsignarCategoria(temporadaId, equipo);
    }

    @PostMapping("/cuotas/editar")
    public String editarCuota(@RequestParam Long id, @RequestParam Long conceptoPagoId,
            @RequestParam(required = false) String periodo, @RequestParam(required = false) String fechaLimite,
            @RequestParam(required = false) String observaciones,
            @RequestParam(defaultValue = "categoria") String origen, @RequestParam(required = false) Long temporadaId,
            @RequestParam(required = false) String equipo,
            RedirectAttributes redirect) {
        logger.debug("Inicio editarCuota: id={}, conceptoPagoId={}", id, conceptoPagoId);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(id);
        ConceptoPagoDTO concepto = conceptoPagoService.obtenerPorId(conceptoPagoId);
        if (cuota == null || concepto == null || concepto.getImporte() == null) {
            logger.warn("Cuota o concepto no encontrado al editar: id={}, conceptoPagoId={}", id, conceptoPagoId);
            redirect.addFlashAttribute("error", "La cuota o el concepto no existe.");
        } else {
            cuota.setConceptoPagoId(conceptoPagoId);
            cuota.setPeriodo(periodo);
            cuota.setImporte(concepto.getImporte());
            cuota.setFechaLimite(parseDate(fechaLimite));
            cuota.setObservaciones(observaciones);
            cuotaJugadorService.guardarCuota(cuota);
            redirect.addFlashAttribute("mensaje", "Cuota actualizada correctamente.");
        }
        logger.debug("Fin editarCuota: id={}", id);
        return redirectCuotas(origen, temporadaId, equipo);
    }

    @PostMapping("/cuotas/borrar")
    public String borrarCuota(@RequestParam Long id, @RequestParam(defaultValue = "categoria") String origen,
            @RequestParam(required = false) Long temporadaId, @RequestParam(required = false) String equipo,
            RedirectAttributes redirect) {
        logger.debug("Inicio borrarCuota: id={}", id);
        cuotaJugadorService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Cuota eliminada correctamente.");
        logger.debug("Fin borrarCuota: id={}", id);
        return redirectCuotas(origen, temporadaId, equipo);
    }

    @PostMapping("/cuotas/borrar-multiple")
    public String borrarCuotasSeleccionadas(@RequestParam(value = "cuotaIds", required = false) List<Long> cuotaIds,
            @RequestParam(required = false) Long temporadaId, @RequestParam(required = false) String equipo,
            RedirectAttributes redirect) {
        logger.debug("Inicio borrarCuotasSeleccionadas: cuotaIds={}, temporadaId={}, equipo={}", cuotaIds, temporadaId,
                equipo);
        if (cuotaIds == null || cuotaIds.isEmpty()) {
            redirect.addFlashAttribute("error", "No hay cuotas seleccionadas para borrar.");
            logger.debug("Fin borrarCuotasSeleccionadas: sin seleccion");
            return redirectAsignarCategoria(temporadaId, equipo);
        }
        cuotaJugadorService.eliminarEnLote(cuotaIds);
        redirect.addFlashAttribute("mensaje", "Se eliminaron " + cuotaIds.size() + " cuotas correctamente.");
        logger.debug("Fin borrarCuotasSeleccionadas: total={}", cuotaIds.size());
        return redirectAsignarCategoria(temporadaId, equipo);
    }

    private CuotaJugadorDTO crearCuota(Long jugadorId, Long conceptoPagoId, BigDecimal importe, String periodo,
            String fechaLimite, String observaciones) {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setJugadorId(jugadorId);
        cuota.setConceptoPagoId(conceptoPagoId);
        cuota.setPeriodo(periodo);
        cuota.setImporte(importe);
        cuota.setEstado("PENDIENTE");
        cuota.setFechaLimite(parseDate(fechaLimite));
        cuota.setObservaciones(observaciones);
        return cuota;
    }

    private List<String> generarPeriodos(String periodoInicio, String periodoFin) {
        if (periodoInicio == null || periodoFin == null || periodoInicio.isBlank() || periodoFin.isBlank()) {
            return List.of();
        }
        try {
            YearMonth inicio = YearMonth.parse(periodoInicio);
            YearMonth fin = YearMonth.parse(periodoFin);
            if (inicio.isAfter(fin) || inicio.plusMonths(24).isBefore(fin)) {
                return List.of();
            }
            List<String> periodos = new ArrayList<>();
            for (YearMonth periodo = inicio; !periodo.isAfter(fin); periodo = periodo.plusMonths(1)) {
                periodos.add(periodo.toString());
            }
            return periodos;
        } catch (DateTimeParseException exception) {
            return List.of();
        }
    }

    private boolean esCuotaMensual(ConceptoPagoDTO concepto) {
        return concepto != null && concepto.getNombre() != null
                && concepto.getNombre().trim().toUpperCase(java.util.Locale.ROOT).contains("CUOTA");
    }

    private String[] obtenerPeriodosTemporada(TemporadaDTO temporada) {
        if (temporada != null && temporada.getNombre() != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{4})\\s*[/\\-]\\s*(\\d{4})")
                    .matcher(temporada.getNombre());
            if (matcher.find()) {
                return new String[] { matcher.group(1) + "-09", matcher.group(2) + "-06" };
            }
        }
        int inicio = temporada != null && temporada.getFechaInicio() != null
                ? temporada.getFechaInicio().toInstant().atZone(java.time.ZoneId.systemDefault()).getYear()
                : YearMonth.now().getYear();
        return new String[] { inicio + "-09", (inicio + 1) + "-06" };
    }

    private boolean existeCuotaAsignada(Long jugadorId, Long conceptoPagoId, String periodo) {
        return cuotaJugadorService.obtenerTodos().stream()
                .anyMatch(cuota -> cuota != null
                        && cuota.getJugadorId() != null && cuota.getJugadorId().equals(jugadorId)
                        && cuota.getConceptoPagoId() != null && cuota.getConceptoPagoId().equals(conceptoPagoId)
                        && (periodo == null ? cuota.getPeriodo() == null : periodo.equals(cuota.getPeriodo())));
    }

    private static String redirectAsignarCategoria(Long temporadaId, String equipo) {
        StringBuilder ruta = new StringBuilder("/admin/pagos/asignar-cuotas");
        if (temporadaId != null) {
            ruta.append("?temporadaId=").append(temporadaId);
            if (equipo != null && !equipo.isBlank()) {
                ruta.append("&equipo=").append(equipo);
            }
        }
        return "redirect:" + ruta;
    }

    private static String redirectCuotas(String origen, Long temporadaId, String equipo) {
        return redirectAsignarCategoria(temporadaId, equipo);
    }

    @PostMapping("/registrar")
    public String registrarPago(@RequestParam(required = false) Long id, @RequestParam Long cuotaJugadorId,
            @RequestParam BigDecimal importe,
            @RequestParam String fechaPago, @RequestParam String metodoPago,
            @RequestParam(required = false) String referencia, @RequestParam(required = false) String observaciones,
            RedirectAttributes redirect) {
        logger.debug("Inicio registrarPago: id={}, cuotaJugadorId={}, importe={}", id, cuotaJugadorId, importe);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(cuotaJugadorId);
        BigDecimal pagosActuales = pagoService.obtenerTotalPagado(cuotaJugadorId);
        PagoDTO pagoActual = id == null ? null : pagoService.obtenerPorId(id);
        if (pagoActual != null) {
            pagosActuales = pagosActuales.subtract(zeroIfNull(pagoActual.getImporte()));
        }
        BigDecimal pendiente = cuota == null ? BigDecimal.ZERO
                : zeroIfNull(cuota.getImporte()).subtract(pagosActuales);
        if (cuota == null || importe == null || importe.signum() <= 0 || importe.compareTo(pendiente) > 0) {
            logger.warn("Importe de pago invalido: cuotaJugadorId={}, importe={}, pendiente={}", cuotaJugadorId,
                    importe,
                    pendiente);
            redirect.addFlashAttribute("error", "El importe supera el saldo pendiente o la cuota no existe.");
            logger.debug("Fin registrarPago: resultado=INVALIDO");
            return "redirect:/admin/pagos";
        }
        PagoDTO pago = new PagoDTO();
        pago.setId(id);
        pago.setCuotaJugadorId(cuotaJugadorId);
        pago.setImporte(importe);
        pago.setFechaPago(parseDate(fechaPago));
        pago.setMetodoPago(metodoPago);
        pago.setReferencia(referencia);
        pago.setObservaciones(observaciones);
        pagoService.guardarPago(pago);
        cuotaJugadorService.actualizarEstado(cuotaJugadorId);
        redirect.addFlashAttribute("mensaje", "Pago registrado correctamente.");
        logger.debug("Fin registrarPago: cuotaJugadorId={}", cuotaJugadorId);
        return "redirect:/admin/pagos";
    }

    @PostMapping(value = "/registrar-ajax", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registrarPagoAjax(@RequestParam Long cuotaJugadorId,
            @RequestParam BigDecimal importe, @RequestParam String fechaPago, @RequestParam String metodoPago) {
        logger.debug("Inicio registrarPagoAjax: cuotaJugadorId={}, importe={}", cuotaJugadorId, importe);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(cuotaJugadorId);
        BigDecimal pagadoActual = pagoService.obtenerTotalPagado(cuotaJugadorId);
        BigDecimal pendiente = cuota == null ? BigDecimal.ZERO
                : zeroIfNull(cuota.getImporte()).subtract(pagadoActual);
        if (cuota == null || importe == null || importe.signum() <= 0 || importe.compareTo(pendiente) > 0) {
            logger.warn("Importe de pago AJAX invalido: cuotaJugadorId={}, importe={}, pendiente={}", cuotaJugadorId,
                    importe, pendiente);
            logger.debug("Fin registrarPagoAjax: resultado=INVALIDO");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El importe supera el saldo pendiente o la cuota no existe."));
        }
        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(cuotaJugadorId);
        pago.setImporte(importe);
        pago.setFechaPago(parseDate(fechaPago));
        pago.setMetodoPago(metodoPago);
        pagoService.guardarPago(pago);
        try {
            cuotaJugadorService.actualizarEstado(cuotaJugadorId);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            logger.warn("No se pudo sincronizar el estado de la cuota tras registrar el pago: cuotaJugadorId={}",
                    cuotaJugadorId, exception);
        }

        BigDecimal pagado = pagoService.obtenerTotalPagado(cuotaJugadorId);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("cuotaJugadorId", cuotaJugadorId);
        respuesta.put("pago", pago);
        respuesta.put("pagado", pagado);
        respuesta.put("pendiente", zeroIfNull(cuota.getImporte()).subtract(pagado).max(BigDecimal.ZERO));
        logger.debug("Fin registrarPagoAjax: cuotaJugadorId={}, pagado={}", cuotaJugadorId, pagado);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping(value = "/editar-ajax", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editarPagoAjax(@RequestParam Long id,
            @RequestParam Long cuotaJugadorId, @RequestParam BigDecimal importe,
            @RequestParam String fechaPago, @RequestParam String metodoPago) {
        logger.debug("Inicio editarPagoAjax: id={}, cuotaJugadorId={}, importe={}", id, cuotaJugadorId, importe);
        PagoDTO pago = pagoService.obtenerPorId(id);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(cuotaJugadorId);
        BigDecimal totalSinPago = pagoService.obtenerTotalPagado(cuotaJugadorId)
                .subtract(pago == null ? BigDecimal.ZERO : zeroIfNull(pago.getImporte()));
        BigDecimal pendiente = cuota == null ? BigDecimal.ZERO
                : zeroIfNull(cuota.getImporte()).subtract(totalSinPago);
        if (pago == null || cuota == null || importe == null || importe.signum() <= 0
                || !cuotaJugadorId.equals(pago.getCuotaJugadorId()) || importe.compareTo(pendiente) > 0) {
            logger.warn("Edicion de pago AJAX invalida: id={}, cuotaJugadorId={}", id, cuotaJugadorId);
            logger.debug("Fin editarPagoAjax: resultado=INVALIDO");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El importe supera el saldo pendiente o el pago no existe."));
        }
        pago.setCuotaJugadorId(cuotaJugadorId);
        pago.setImporte(importe);
        pago.setFechaPago(parseDate(fechaPago));
        pago.setMetodoPago(metodoPago);
        pagoService.guardarPago(pago);
        cuotaJugadorService.actualizarEstado(cuotaJugadorId);
        BigDecimal pagadoActualizado = pagoService.obtenerTotalPagado(cuotaJugadorId);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("pago", pago);
        respuesta.put("pagado", pagadoActualizado);
        respuesta.put("pendiente", zeroIfNull(cuota.getImporte()).subtract(pagadoActualizado).max(BigDecimal.ZERO));
        logger.debug("Fin editarPagoAjax: id={}, pagado={}", id, pagadoActualizado);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping(value = "/borrar-ajax", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> borrarPagoAjax(@RequestParam Long id) {
        logger.debug("Inicio borrarPagoAjax: id={}", id);
        PagoDTO pago = pagoService.obtenerPorId(id);
        if (pago == null) {
            logger.warn("Pago no encontrado al borrar: id={}", id);
            logger.debug("Fin borrarPagoAjax: resultado=NO_ENCONTRADO");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "El pago no existe."));
        }
        Long cuotaJugadorId = pago.getCuotaJugadorId();
        pagoService.eliminar(id);
        cuotaJugadorService.actualizarEstado(cuotaJugadorId);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(cuotaJugadorId);
        BigDecimal pagado = pagoService.obtenerTotalPagado(cuotaJugadorId);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("cuotaJugadorId", cuotaJugadorId);
        respuesta.put("pagado", pagado);
        respuesta.put("pendiente", cuota == null ? BigDecimal.ZERO
                : zeroIfNull(cuota.getImporte()).subtract(pagado).max(BigDecimal.ZERO));
        logger.debug("Fin borrarPagoAjax: id={}, cuotaJugadorId={}", id, cuotaJugadorId);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/becado")
    public String marcarJugadorBecado(@RequestParam Long jugadorId, @RequestParam(required = false) Long temporadaId,
            RedirectAttributes redirect) {
        logger.debug("Inicio marcarJugadorBecado: jugadorId={}, temporadaId={}", jugadorId, temporadaId);
        try {
            JugadorDTO jugador = jugadorService.obtenerJugadorPorId(jugadorId);
            if (jugador == null) {
                redirect.addFlashAttribute("error", "El jugador seleccionado no existe.");
                logger.warn("Fin marcarJugadorBecado: jugador no encontrado, jugadorId={}", jugadorId);
                return redirigirDashboard(temporadaId);
            }

            int cuotasPagadas = becadoService.marcarCuotasComoBecadas(jugadorId);

            redirect.addFlashAttribute("mensaje", cuotasPagadas == 0
                    ? "El jugador ya tiene todas sus cuotas pagadas."
                    : "Se han marcado " + cuotasPagadas + " cuotas como pagadas por beca.");
            logger.info("Jugador becado: jugadorId={}, cuotasPagadas={}", jugadorId, cuotasPagadas);
            logger.debug("Fin marcarJugadorBecado: jugadorId={}, cuotasPagadas={}", jugadorId, cuotasPagadas);
        } catch (Exception exception) {
            logger.error("Error al marcar jugador como becado: jugadorId={}, error={}", jugadorId,
                    exception.getMessage(), exception);
            redirect.addFlashAttribute("error", "No se han podido actualizar las cuotas del jugador.");
            logger.debug("Fin marcarJugadorBecado: jugadorId={}, resultado=ERROR", jugadorId);
        }
        return redirigirDashboard(temporadaId);
    }

    @PostMapping("/borrar")
    public String borrarPago(@RequestParam Long id, RedirectAttributes redirect) {
        logger.debug("Inicio borrarPago: id={}", id);
        PagoDTO pago = pagoService.obtenerPorId(id);
        pagoService.eliminar(id);
        if (pago != null) {
            cuotaJugadorService.actualizarEstado(pago.getCuotaJugadorId());
        }
        redirect.addFlashAttribute("mensaje", "Pago eliminado correctamente.");
        logger.debug("Fin borrarPago: id={}", id);
        return "redirect:/admin/pagos";
    }

    private static String redirigirDashboard(Long temporadaId) {
        return temporadaId == null ? "redirect:/admin/pagos"
                : "redirect:/admin/pagos?temporadaId=" + temporadaId;
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new SimpleDateFormat(DATE_FORMAT).parse(value);
        } catch (ParseException exception) {
            throw new IllegalArgumentException("Fecha no válida: " + value, exception);
        }
    }

    private static String formatearPeriodo(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            return "-";
        }
        try {
            String texto = YearMonth.parse(periodo)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")));
            return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        } catch (DateTimeParseException exception) {
            return periodo;
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}