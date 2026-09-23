package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ResultadoDao;
import com.mikedev.mutxamelcf.model.Resultado;
import com.mikedev.mutxamelcf.model.ResultadoDTO;

@ExtendWith(MockitoExtension.class)
class ResultadoServiceImplTest {

    @Mock
    private ResultadoDao resultadoDao;

    private ResultadoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResultadoServiceImpl(resultadoDao);
    }

    @Test
    void actualizarResultadoDelegaEnElDao() {
        ResultadoDTO dto = new ResultadoDTO();
        dto.setCategoria("SENIOR");
        dto.setEquipo("Senior A");

        service.actualizarResultado(dto);

        verify(resultadoDao).actualizarResultado(any(Resultado.class));
    }

    @Test
    void obtenerResultadosFormateaLaFechaCuandoExiste() throws Exception {
        Date fecha = new SimpleDateFormat("dd/MM/yyyy").parse("01/03/2026");

        Resultado resultado = new Resultado();
        resultado.setCategoria("SENIOR");
        resultado.setDia(fecha);

        when(resultadoDao.obtenerResultados("F")).thenReturn(List.of(resultado));

        List<ResultadoDTO> resultados = service.obtenerResultados("F");

        assertThat(resultados.get(0).getDiaFormateado()).isEqualTo("01/03/2026");
    }

    @Test
    void obtenerResultadosDejaDiaFormateadoVacioCuandoNoHayFecha() {
        Resultado resultado = new Resultado();
        resultado.setCategoria("SENIOR");
        resultado.setDia(null);

        when(resultadoDao.obtenerResultados("F")).thenReturn(List.of(resultado));

        List<ResultadoDTO> resultados = service.obtenerResultados("F");

        assertThat(resultados.get(0).getDiaFormateado()).isEmpty();
        assertThat(resultados.get(0).getDia()).isNull();
    }

    @Test
    void obtenerResultadoPrimerEquipoDevuelveNullCuandoNoExiste() {
        when(resultadoDao.obtenerResultadoPrimerEquipo()).thenReturn(null);

        assertThat(service.obtenerResultadoPrimerEquipo()).isNull();
    }

    @Test
    void obtenerResultadoPrimerEquipoMapeaElResultado() {
        Resultado resultado = new Resultado();
        resultado.setEquipo("Mutxamel CF");

        when(resultadoDao.obtenerResultadoPrimerEquipo()).thenReturn(resultado);

        assertThat(service.obtenerResultadoPrimerEquipo().getEquipo()).isEqualTo("Mutxamel CF");
    }
}
