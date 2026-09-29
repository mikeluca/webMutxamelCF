package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.CuotaJugadorDao;
import com.mikedev.mutxamelcf.dao.PagoDao;
import com.mikedev.mutxamelcf.model.CuotaJugador;
import com.mikedev.mutxamelcf.model.Pago;
import com.mikedev.mutxamelcf.model.PagoDTO;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    @Mock
    private PagoDao pagoDao;

    @Mock
    private CuotaJugadorDao cuotaJugadorDao;

    private PagoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PagoServiceImpl(pagoDao, cuotaJugadorDao);
    }

    private static CuotaJugador cuota(Long id, String importe) {
        CuotaJugador cuota = new CuotaJugador();
        cuota.setId(id);
        cuota.setImporte(new BigDecimal(importe));
        return cuota;
    }

    @Test
    void guardarPagoTrasladaElIdGeneradoAlDto() {
        when(pagoDao.guardarPago(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            pago.setId(77L);
            return true;
        });

        PagoDTO dto = new PagoDTO();
        dto.setCuotaJugadorId(1L);
        dto.setImporte(new BigDecimal("20.00"));

        boolean resultado = service.guardarPago(dto);

        assertThat(resultado).isTrue();
        assertThat(dto.getId()).isEqualTo(77L);
    }

    @Test
    void guardarPagoNoTocaElDtoSiFalla() {
        when(pagoDao.guardarPago(any(Pago.class))).thenReturn(false);

        PagoDTO dto = new PagoDTO();

        assertThat(service.guardarPago(dto)).isFalse();
        assertThat(dto.getId()).isNull();
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        when(pagoDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerPorIdMapeaLosCampos() {
        Pago pago = new Pago();
        pago.setId(1L);
        pago.setCuotaJugadorId(2L);
        pago.setImporte(new BigDecimal("15.00"));
        pago.setMetodoPago("EFECTIVO");

        when(pagoDao.obtenerPorId(1L)).thenReturn(pago);

        PagoDTO resultado = service.obtenerPorId(1L);

        assertThat(resultado.getMetodoPago()).isEqualTo("EFECTIVO");
        assertThat(resultado.getImporte()).isEqualByComparingTo("15.00");
    }

    @Test
    void obtenerPorCuotaDelegaEnElDao() {
        when(pagoDao.obtenerPorCuota(1L)).thenReturn(List.of(new Pago()));

        assertThat(service.obtenerPorCuota(1L)).hasSize(1);
    }

    @Test
    void obtenerPorCuotasDelegaEnElDao() {
        when(pagoDao.obtenerPorCuotas(List.of(1L, 2L))).thenReturn(List.of(new Pago(), new Pago()));

        assertThat(service.obtenerPorCuotas(List.of(1L, 2L))).hasSize(2);
    }

    @Test
    void obtenerTotalPagadoDelegaEnElDao() {
        when(pagoDao.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("40.00"));

        assertThat(service.obtenerTotalPagado(1L)).isEqualByComparingTo("40.00");
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(pagoDao).eliminar(1L);
    }

    @Test
    void registrarPagoBloqueaLaCuotaValidaEInsertaYActualizaElEstado() {
        when(cuotaJugadorDao.bloquearPorId(1L)).thenReturn(cuota(1L, "100.00"));
        when(pagoDao.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("40.00"));
        when(pagoDao.guardarPago(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            pago.setId(9L);
            return true;
        });

        PagoDTO datos = new PagoDTO();
        datos.setCuotaJugadorId(1L);
        datos.setImporte(new BigDecimal("60.00"));

        PagoDTO resultado = service.registrarPago(datos);

        assertThat(resultado.getId()).isEqualTo(9L);
        verify(cuotaJugadorDao).bloquearPorId(1L);
        verify(cuotaJugadorDao).actualizarEstado(1L);
    }

    @Test
    void registrarPagoRechazaSiLaCuotaNoExiste() {
        when(cuotaJugadorDao.bloquearPorId(1L)).thenReturn(null);

        PagoDTO datos = new PagoDTO();
        datos.setCuotaJugadorId(1L);
        datos.setImporte(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.registrarPago(datos))
                .isInstanceOf(IllegalArgumentException.class);

        verify(pagoDao, never()).guardarPago(any());
        verify(cuotaJugadorDao, never()).actualizarEstado(any());
    }

    @Test
    void registrarPagoRechazaSiElImporteSuperaElPendiente() {
        // Esta es exactamente la carrera de BE-01: el pendiente se calcula
        // DENTRO de la transaccion, tras bloquear la cuota, no antes.
        when(cuotaJugadorDao.bloquearPorId(1L)).thenReturn(cuota(1L, "100.00"));
        when(pagoDao.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("90.00"));

        PagoDTO datos = new PagoDTO();
        datos.setCuotaJugadorId(1L);
        datos.setImporte(new BigDecimal("20.00"));

        assertThatThrownBy(() -> service.registrarPago(datos))
                .isInstanceOf(IllegalArgumentException.class);

        verify(pagoDao, never()).guardarPago(any());
    }

    @Test
    void registrarPagoRechazaEditarUnPagoDeOtraCuota() {
        when(cuotaJugadorDao.bloquearPorId(1L)).thenReturn(cuota(1L, "100.00"));
        Pago pagoDeOtraCuota = new Pago();
        pagoDeOtraCuota.setId(5L);
        pagoDeOtraCuota.setCuotaJugadorId(2L);
        when(pagoDao.obtenerPorId(5L)).thenReturn(pagoDeOtraCuota);

        PagoDTO datos = new PagoDTO();
        datos.setId(5L);
        datos.setCuotaJugadorId(1L);
        datos.setImporte(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.registrarPago(datos))
                .isInstanceOf(IllegalArgumentException.class);

        verify(pagoDao, never()).guardarPago(any());
    }

    @Test
    void registrarPagoAlEditarDescuentaElImporteActualDelPagoDelPendiente() {
        when(cuotaJugadorDao.bloquearPorId(1L)).thenReturn(cuota(1L, "100.00"));
        Pago pagoExistente = new Pago();
        pagoExistente.setId(5L);
        pagoExistente.setCuotaJugadorId(1L);
        pagoExistente.setImporte(new BigDecimal("30.00"));
        when(pagoDao.obtenerPorId(5L)).thenReturn(pagoExistente);
        // Total pagado incluye el propio pago que se esta editando (30):
        // el pendiente real antes de esta edicion es 100 - 30 = 70.
        when(pagoDao.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("30.00"));
        when(pagoDao.guardarPago(any(Pago.class))).thenReturn(true);

        PagoDTO datos = new PagoDTO();
        datos.setId(5L);
        datos.setCuotaJugadorId(1L);
        datos.setImporte(new BigDecimal("70.00"));

        PagoDTO resultado = service.registrarPago(datos);

        assertThat(resultado).isNotNull();
        verify(pagoDao).guardarPago(any(Pago.class));
    }
}
