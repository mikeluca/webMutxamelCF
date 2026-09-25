package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.NoticiaDao;
import com.mikedev.mutxamelcf.model.Noticia;
import com.mikedev.mutxamelcf.model.NoticiaAppDTO;
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

    @Test
    void eliminarNoticiaDelegaEnElDao() {
        service.eliminarNoticia(1);

        verify(noticiaDao).eliminarNoticia(1);
    }

    @Test
    void obtenerNoticiaPorIdDevuelveNullCuandoNoExiste() {
        when(noticiaDao.obtenerNoticiaPorId(99)).thenReturn(null);

        assertThat(service.obtenerNoticiaPorId(99)).isNull();
    }

    @Test
    void obtenerNoticiaPorIdCodificaLaImagenEnBase64() {
        Noticia noticia = new Noticia();
        noticia.setId(1);
        noticia.setTitulo("Titulo");
        noticia.setImagen(new byte[] { 1, 2, 3 });

        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        NoticiaDTO resultado = service.obtenerNoticiaPorId(1);

        assertThat(resultado.getImagenBase64()).isEqualTo(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
    }

    @Test
    void obtenerNoticiaPorIdSinImagenDejaImagenBase64Null() {
        Noticia noticia = new Noticia();
        noticia.setId(1);
        noticia.setImagen(null);

        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        assertThat(service.obtenerNoticiaPorId(1).getImagenBase64()).isNull();
    }

    @Test
    void obtenerNoticiasParaMostrarDelegaEnElDao() {
        when(noticiaDao.obtenerNoticiasParaMostrar()).thenReturn(List.of(new Noticia()));

        assertThat(service.obtenerNoticiasParaMostrar()).hasSize(1);
    }

    @Test
    void obtenerTodasDelegaEnElDao() {
        when(noticiaDao.obtenerTodas()).thenReturn(List.of(new Noticia(), new Noticia()));

        assertThat(service.obtenerTodas()).hasSize(2);
    }

    @Test
    void obtenerNoticiasParaAppPaginaConstruyeUrlDeImagenSoloSiHayImagen() {
        Noticia conImagen = new Noticia();
        conImagen.setId(1);
        conImagen.setImagen(new byte[] { 1 });

        Noticia sinImagen = new Noticia();
        sinImagen.setId(2);
        sinImagen.setImagen(null);

        when(noticiaDao.obtenerNoticiasPagina(null, 5)).thenReturn(List.of(conImagen, sinImagen));

        List<NoticiaAppDTO> resultado = service.obtenerNoticiasParaAppPagina(null, null);

        assertThat(resultado.get(0).getImagenUrl()).isEqualTo("/api/public/noticias/1/imagen");
        assertThat(resultado.get(1).getImagenUrl()).isNull();
    }

    @Test
    void obtenerNoticiasParaAppPaginaSinLimiteUsaCinco() {
        when(noticiaDao.obtenerNoticiasPagina(null, 5)).thenReturn(List.of());

        service.obtenerNoticiasParaAppPagina(null, null);

        verify(noticiaDao).obtenerNoticiasPagina(null, 5);
    }

    @Test
    void obtenerNoticiasParaAppPaginaConAntesIdLoPasaAlDao() {
        when(noticiaDao.obtenerNoticiasPagina(42, 5)).thenReturn(List.of());

        service.obtenerNoticiasParaAppPagina(42, null);

        verify(noticiaDao).obtenerNoticiasPagina(42, 5);
    }

    @Test
    void obtenerNoticiasParaAppPaginaAcotaElLimiteMaximo() {
        when(noticiaDao.obtenerNoticiasPagina(null, 20)).thenReturn(List.of());

        service.obtenerNoticiasParaAppPagina(null, 1000);

        verify(noticiaDao).obtenerNoticiasPagina(null, 20);
    }

    @Test
    void obtenerNoticiasParaAppPaginaAcotaElLimiteMinimo() {
        when(noticiaDao.obtenerNoticiasPagina(null, 1)).thenReturn(List.of());

        service.obtenerNoticiasParaAppPagina(null, -5);

        verify(noticiaDao).obtenerNoticiasPagina(null, 1);
    }

    @Test
    void obtenerNoticiaParaAppDevuelveNullCuandoNoExiste() {
        when(noticiaDao.obtenerNoticiaPorId(99)).thenReturn(null);

        assertThat(service.obtenerNoticiaParaApp(99)).isNull();
    }

    @Test
    void obtenerNoticiaParaAppMapeaCorrectamente() {
        Noticia noticia = new Noticia();
        noticia.setId(1);
        noticia.setTitulo("Titulo");
        noticia.setContenido("Contenido");
        noticia.setFecha(new Date());
        noticia.setImagen(new byte[] { 1 });

        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        NoticiaAppDTO resultado = service.obtenerNoticiaParaApp(1);

        assertThat(resultado.getTitulo()).isEqualTo("Titulo");
        assertThat(resultado.getImagenUrl()).isEqualTo("/api/public/noticias/1/imagen");
    }

    @Test
    void obtenerNoticiaParaAppDecodificaEntidadesHtmlYConvierteBrEnSaltosDeLinea() {
        // Tal como los guarda NoticiaAdminController.guardarNoticia:
        // HtmlUtils.htmlEscape(...) + "\n" -> "<br>".
        Noticia noticia = new Noticia();
        noticia.setId(1);
        noticia.setTitulo("Informaci&oacute;n del club");
        noticia.setContenido("Primer p&aacute;rrafo.<br><br>Segundo p&aacute;rrafo con &ntilde;.");
        noticia.setFecha(new Date());

        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        NoticiaAppDTO resultado = service.obtenerNoticiaParaApp(1);

        assertThat(resultado.getTitulo()).isEqualTo("Información del club");
        assertThat(resultado.getContenido())
                .isEqualTo("Primer párrafo.\n\nSegundo párrafo con ñ.");
    }

    @Test
    void obtenerImagenNoticiaDevuelveNullCuandoNoExisteLaNoticia() {
        when(noticiaDao.obtenerNoticiaPorId(99)).thenReturn(null);

        assertThat(service.obtenerImagenNoticia(99)).isNull();
    }

    @Test
    void obtenerImagenNoticiaDevuelveLaImagenAlmacenada() {
        Noticia noticia = new Noticia();
        noticia.setImagen(new byte[] { 9, 8, 7 });
        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        assertThat(service.obtenerImagenNoticia(1)).containsExactly(9, 8, 7);
    }

    @Test
    void obtenerImagenNoticiaMiniDevuelveNullSiNoExisteLaNoticia() {
        when(noticiaDao.obtenerNoticiaPorId(99)).thenReturn(null);

        assertThat(service.obtenerImagenNoticiaMini(99)).isNull();
    }

    @Test
    void obtenerImagenNoticiaMiniDevuelveNullSiNoTieneImagen() {
        Noticia noticia = new Noticia();
        noticia.setImagen(null);
        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        assertThat(service.obtenerImagenNoticiaMini(1)).isNull();
    }

    @Test
    void obtenerImagenNoticiaMiniDevuelveNullSiLaImagenNoEsValida() {
        Noticia noticia = new Noticia();
        noticia.setImagen(new byte[] { 1, 2, 3 });
        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        assertThat(service.obtenerImagenNoticiaMini(1)).isNull();
    }

    @Test
    void obtenerImagenNoticiaMiniNoAmpliaImagenesPequenas() throws Exception {
        Noticia noticia = new Noticia();
        noticia.setImagen(imagenJpg(50, 40));
        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        byte[] mini = service.obtenerImagenNoticiaMini(1);

        BufferedImage decodificada = ImageIO.read(new java.io.ByteArrayInputStream(mini));
        assertThat(decodificada.getWidth()).isEqualTo(50);
        assertThat(decodificada.getHeight()).isEqualTo(40);
    }

    @Test
    void obtenerImagenNoticiaMiniRedimensionaImagenesGrandes() throws Exception {
        Noticia noticia = new Noticia();
        noticia.setImagen(imagenJpg(1600, 800));
        when(noticiaDao.obtenerNoticiaPorId(1)).thenReturn(noticia);

        byte[] mini = service.obtenerImagenNoticiaMini(1);

        BufferedImage decodificada = ImageIO.read(new java.io.ByteArrayInputStream(mini));
        assertThat(decodificada.getWidth()).isEqualTo(800);
        assertThat(decodificada.getHeight()).isEqualTo(400);
    }

    private static byte[] imagenJpg(int ancho, int alto) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }
}
