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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.model.DispositivoApp;
import com.mikedev.mutxamelcf.model.DispositivoAppRequest;

@ExtendWith(MockitoExtension.class)
class DispositivoAppServiceImplTest {

    @Mock
    private DispositivoAppDao dispositivoAppDao;

    private DispositivoAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DispositivoAppServiceImpl(dispositivoAppDao);
    }

    @Test
    void registrarLanzaExcepcionSiElUsuarioEsNull() {
        assertThatThrownBy(() -> service.registrar(null, new DispositivoAppRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarLanzaExcepcionSiElRequestEsNull() {
        assertThatThrownBy(() -> service.registrar(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarLanzaExcepcionSiElTokenEsVacio() {
        DispositivoAppRequest request = new DispositivoAppRequest();
        request.setTokenFcm("   ");

        assertThatThrownBy(() -> service.registrar(1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarActualizaElAccesoSiElDispositivoYaExiste() {
        DispositivoAppRequest request = new DispositivoAppRequest();
        request.setTokenFcm(" token-abc ");
        request.setPlataforma("android");

        when(dispositivoAppDao.obtenerPorUsuarioYToken(1L, "token-abc")).thenReturn(new DispositivoApp());

        service.registrar(1L, request);

        verify(dispositivoAppDao).desactivarTokenDeOtrosUsuarios(1L, "token-abc");
        verify(dispositivoAppDao).actualizarAcceso(1L, "token-abc");
        verify(dispositivoAppDao, never()).registrar(any());
    }

    @Test
    void registrarCreaUnDispositivoNuevoConPlataformaPorDefecto() {
        DispositivoAppRequest request = new DispositivoAppRequest();
        request.setTokenFcm("token-abc");
        request.setPlataforma(null);

        when(dispositivoAppDao.obtenerPorUsuarioYToken(1L, "token-abc")).thenReturn(null);

        service.registrar(1L, request);

        ArgumentCaptor<DispositivoApp> captor = ArgumentCaptor.forClass(DispositivoApp.class);
        verify(dispositivoAppDao).registrar(captor.capture());
        assertThat(captor.getValue().getPlataforma()).isEqualTo("ANDROID");
        assertThat(captor.getValue().getActivo()).isEqualTo(1);
    }

    @Test
    void registrarNormalizaLaPlataformaAMayusculas() {
        DispositivoAppRequest request = new DispositivoAppRequest();
        request.setTokenFcm("token-abc");
        request.setPlataforma(" ios ");

        when(dispositivoAppDao.obtenerPorUsuarioYToken(1L, "token-abc")).thenReturn(null);

        service.registrar(1L, request);

        ArgumentCaptor<DispositivoApp> captor = ArgumentCaptor.forClass(DispositivoApp.class);
        verify(dispositivoAppDao).registrar(captor.capture());
        assertThat(captor.getValue().getPlataforma()).isEqualTo("IOS");
    }

    @Test
    void desactivarNoHaceNadaSiFaltanDatos() {
        service.desactivar(null, "token");
        service.desactivar(1L, null);
        service.desactivar(1L, "  ");

        verify(dispositivoAppDao, never()).desactivar(any(), any());
    }

    @Test
    void desactivarDelegaEnElDaoConElTokenRecortado() {
        service.desactivar(1L, " token-abc ");

        verify(dispositivoAppDao).desactivar(1L, "token-abc");
    }
}
