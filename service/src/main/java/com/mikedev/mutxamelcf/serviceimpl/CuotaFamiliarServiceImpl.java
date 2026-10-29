package com.mikedev.mutxamelcf.serviceimpl;

import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaFamiliarResponse;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PagoCuotaResponse;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaFamiliarService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

@Service
public class CuotaFamiliarServiceImpl implements CuotaFamiliarService {

    private final PerfilAppDao perfilAppDao;
    private final CuotaJugadorService cuotaJugadorService;
    private final ConceptoPagoService conceptoPagoService;
    private final PagoService pagoService;
    private final TemporadaService temporadaService;

    public CuotaFamiliarServiceImpl(
            PerfilAppDao perfilAppDao,
            CuotaJugadorService cuotaJugadorService,
            ConceptoPagoService conceptoPagoService,
            PagoService pagoService,
            TemporadaService temporadaService) {

        this.perfilAppDao = perfilAppDao;
        this.cuotaJugadorService = cuotaJugadorService;
        this.conceptoPagoService = conceptoPagoService;
        this.pagoService = pagoService;
        this.temporadaService = temporadaService;
    }

    @Override
    public List<CuotaFamiliarResponse> obtenerCuotasDeMisJugadores(int usuarioAppId) {

        // Las familias solo ven las cuotas de la temporada activa: las de otros años se gestionan en Morosidad.
        TemporadaDTO activa = temporadaService.obtenerTemporadaActiva();

        if (activa == null) {
            return new ArrayList<>();
        }

        List<Jugador> jugadores = perfilAppDao.obtenerJugadoresPorUsuario(usuarioAppId);

        List<CuotaFamiliarResponse> resultado = new ArrayList<>();

        for (Jugador jugador : jugadores) {

            String jugadorNombre = (jugador.getNombre() + " " + jugador.getApellidos()).trim();

            List<CuotaJugadorDTO> cuotas = cuotaJugadorService.obtenerPorJugador(jugador.getId());

            for (CuotaJugadorDTO cuota : cuotas) {

                ConceptoPagoDTO concepto = cuota.getConceptoPagoId() == null ? null
                        : conceptoPagoService.obtenerPorId(cuota.getConceptoPagoId());

                if (concepto == null || !activa.getId().equals(concepto.getTemporadaId())) {
                    continue;
                }

                resultado.add(construirResponse(cuota, concepto, jugadorNombre));
            }
        }

        return resultado;
    }

    private CuotaFamiliarResponse construirResponse(CuotaJugadorDTO cuota, ConceptoPagoDTO concepto,
            String jugadorNombre) {

        /*
         * La columna ESTADO ya se sincroniza correctamente al registrar
         * un pago (CuotaJugadorDaoImpl.actualizarEstado), así que nos
         * fiamos de ella en vez de recalcularla aquí también.
         */
        CuotaFamiliarResponse response = new CuotaFamiliarResponse();

        response.setId(cuota.getId());
        response.setJugadorId(cuota.getJugadorId());
        response.setJugadorNombre(jugadorNombre);
        response.setConcepto(concepto.getNombre() != null ? concepto.getNombre() : "Otros");
        response.setPeriodo(cuota.getPeriodo());
        response.setImporte(cuota.getImporte());
        response.setEstado(cuota.getEstado());
        response.setFechaLimite(cuota.getFechaLimite());
        response.setPeriodoFormateado(formatearPeriodo(cuota.getPeriodo()));
        response.setVencida(esVencida(cuota));
        response.setPagos(construirPagos(pagoService.obtenerPorCuota(cuota.getId())));

        return response;
    }

    private List<PagoCuotaResponse> construirPagos(List<PagoDTO> pagos) {

        List<PagoCuotaResponse> resultado = new ArrayList<>();

        for (PagoDTO pago : pagos) {

            String fechaFormateada = pago.getFechaPago() != null
                    ? new SimpleDateFormat("dd/MM/yyyy").format(pago.getFechaPago())
                    : null;

            resultado.add(new PagoCuotaResponse(pago.getImporte(), fechaFormateada, pago.getMetodoPago()));
        }

        return resultado;
    }

    /**
     * "2026-09" (formato de {@link YearMonth}, tal y como lo guarda
     * PagosController) -> "Septiembre / 2026". Null si la cuota no
     * tiene periodo (no todas las cuotas son mensuales) o si el valor
     * guardado no se puede interpretar como periodo.
     */
    private String formatearPeriodo(String periodo) {

        if (periodo == null || periodo.isBlank()) {
            return null;
        }

        try {

            String texto = YearMonth.parse(periodo)
                    .format(DateTimeFormatter.ofPattern("MMMM / yyyy", new Locale("es", "ES")));

            return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);

        } catch (DateTimeParseException e) {

            return null;
        }
    }

    private boolean esVencida(CuotaJugadorDTO cuota) {

        return !"PAGADO".equals(cuota.getEstado())
                && cuota.getFechaLimite() != null
                && cuota.getFechaLimite().before(new Date());
    }
}
