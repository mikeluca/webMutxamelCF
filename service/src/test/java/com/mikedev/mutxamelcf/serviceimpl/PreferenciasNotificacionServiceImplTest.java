package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.PreferenciasNotificacionDao;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionRequest;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionResponse;

@ExtendWith(MockitoExtension.class)
class PreferenciasNotificacionServiceImplTest {

    @Mock
    private PreferenciasNotificacionDao preferenciasDao;

    private PreferenciasNotificacionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PreferenciasNotificacionServiceImpl(preferenciasDao);
    }

    @Test
    void obtenerPorUsuarioLanzaExcepcionSiElUsuarioEsNull() {
        assertThatThrownBy(() -> service.obtenerPorUsuario(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerPorUsuarioCreaPreferenciasPorDefectoSiNoExisten() {
        when(preferenciasDao.obtenerPorUsuario(1L)).thenReturn(null);

        PreferenciasNotificacionResponse resultado = service.obtenerPorUsuario(1L);

        assertThat(resultado.isNotificacionesActivadas()).isTrue();
        verify(preferenciasDao).guardar(any(PreferenciasNotificacion.class));
    }

    @Test
    void obtenerPorUsuarioDevuelveLasPreferenciasExistentes() {
        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setUsuarioAppId(1L);
        preferencias.setNotificacionesActivadas(0);
        preferencias.setNoticiasActivadas(1);
        preferencias.setComunicacionesActivadas(1);
        preferencias.setMensajesActivados(1);
        preferencias.setResultadosActivados(0);

        when(preferenciasDao.obtenerPorUsuario(1L)).thenReturn(preferencias);

        PreferenciasNotificacionResponse resultado = service.obtenerPorUsuario(1L);

        assertThat(resultado.isNotificacionesActivadas()).isFalse();
        assertThat(resultado.isNoticiasActivadas()).isTrue();
        verify(preferenciasDao, never()).guardar(any());
    }

    @Test
    void actualizarLanzaExcepcionSiElUsuarioEsNull() {
        assertThatThrownBy(() -> service.actualizar(null, new PreferenciasNotificacionRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarLanzaExcepcionSiElRequestEsNull() {
        assertThatThrownBy(() -> service.actualizar(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarGuardaLasPreferenciasYFuerzaComunicacionesActivadas() {
        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setUsuarioAppId(1L);
        when(preferenciasDao.obtenerPorUsuario(1L)).thenReturn(preferencias);

        PreferenciasNotificacionRequest request = new PreferenciasNotificacionRequest();
        request.setNotificacionesActivadas(true);
        request.setNoticiasActivadas(false);
        request.setMensajesActivados(true);
        request.setResultadosActivados(false);

        PreferenciasNotificacionResponse resultado = service.actualizar(1L, request);

        assertThat(resultado.isComunicacionesActivadas()).isTrue();
        assertThat(resultado.isNoticiasActivadas()).isFalse();
        verify(preferenciasDao).actualizar(preferencias);
    }

    @Test
    void puedeRecibirDevuelveFalseSiFaltanDatos() {
        assertThat(service.puedeRecibir(null, "NOTICIA")).isFalse();
        assertThat(service.puedeRecibir(1L, null)).isFalse();
    }

    @Test
    void puedeRecibirDevuelveFalseSiLaPreferenciaGeneralEstaDesactivada() {
        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setNotificacionesActivadas(0);
        preferencias.setNoticiasActivadas(1);

        when(preferenciasDao.obtenerPorUsuario(1L)).thenReturn(preferencias);

        assertThat(service.puedeRecibir(1L, "NOTICIA")).isFalse();
    }

    @Test
    void puedeRecibirComprueboElTipoConcretoCuandoLaGeneralEstaActivada() {
        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setNotificacionesActivadas(1);
        preferencias.setNoticiasActivadas(1);
        preferencias.setComunicacionesActivadas(0);
        preferencias.setMensajesActivados(1);
        preferencias.setResultadosActivados(0);

        when(preferenciasDao.obtenerPorUsuario(1L)).thenReturn(preferencias);

        assertThat(service.puedeRecibir(1L, "noticias")).isTrue();
        assertThat(service.puedeRecibir(1L, "COMUNICACION")).isFalse();
        assertThat(service.puedeRecibir(1L, "MENSAJES")).isTrue();
        assertThat(service.puedeRecibir(1L, "RESULTADO")).isFalse();
        assertThat(service.puedeRecibir(1L, "OTRO")).isFalse();
    }
}
