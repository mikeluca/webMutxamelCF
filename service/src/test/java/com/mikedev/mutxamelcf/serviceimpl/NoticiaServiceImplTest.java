package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.NoticiaDao;
import com.mikedev.mutxamelcf.model.Noticia;
import com.mikedev.mutxamelcf.model.NoticiaDTO;

@ExtendWith(MockitoExtension.class)
class NoticiaServiceImplTest {

    @Mock
    private NoticiaDao noticiaDao;

    private NoticiaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoticiaServiceImpl(noticiaDao);
    }

    @Test
    void guardarNoticiaNuevaPropagaElIdGeneradoDeVueltaAlDto() {

        NoticiaDTO noticia = new NoticiaDTO();
        noticia.setTitulo("Título");
        noticia.setContenido("Contenido");

        // Simula lo que hace el DAO real (GeneratedKeyHolder): rellena el
        // id en la entidad que recibe.
        when(noticiaDao.guardarNoticia(any(Noticia.class))).thenAnswer(invocacion -> {
            Noticia entidad = invocacion.getArgument(0);
            entidad.setId(99);
            return true;
        });

        boolean resultado = service.guardarNoticia(noticia);

        assertThat(resultado).isTrue();
        assertThat(noticia.getId()).isEqualTo(99);
    }

    @Test
    void guardarNoticiaFallidaNoTocaElIdDelDto() {

        NoticiaDTO noticia = new NoticiaDTO();
        noticia.setTitulo("Título");

        when(noticiaDao.guardarNoticia(any(Noticia.class))).thenReturn(false);

        boolean resultado = service.guardarNoticia(noticia);

        assertThat(resultado).isFalse();
        assertThat(noticia.getId()).isEqualTo(0);
    }
}
