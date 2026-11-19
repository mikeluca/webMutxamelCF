package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.ImagenRedesFondoDao;
import com.mikedev.mutxamelcf.model.CartelRedes;
import com.mikedev.mutxamelcf.model.FondoCartel;
import com.mikedev.mutxamelcf.service.FondosCartelesService.Fondo;

class FondosCartelesServiceImplTest {

    private final ImagenRedesFondoDao dao = mock(ImagenRedesFondoDao.class);
    private final FondosCartelesServiceImpl servicio = new FondosCartelesServiceImpl(dao);

    static byte[] imagen(int ancho, int alto, String formato) throws Exception {

        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);

        return salida.toByteArray();
    }

    // ---- subir ---------------------------------------------------------------------------------------

    @Test
    void unPngValidoSeGuardaTalCualConSuAnchoYAlto() throws Exception {

        byte[] png = imagen(1200, 1500, "png");

        servicio.subir(CartelRedes.PRIMER_EQUIPO, png, "admin@club.es");

        verify(dao).guardar("PRIMER_EQUIPO", png, "image/png", 1200, 1500, "admin@club.es");
    }

    @Test
    void unJpgValidoSeGuardaConSuTipo() throws Exception {

        byte[] jpg = imagen(1080, 1350, "jpg");

        servicio.subir(CartelRedes.PRIMER_EQUIPO, jpg, null);

        verify(dao).guardar("PRIMER_EQUIPO", jpg, "image/jpeg", 1080, 1350, null);
    }

    @Test
    void demasiadoPequenaSeRechazaConLasMedidasEnElMensaje() throws Exception {

        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, imagen(1079, 1350, "png"), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("demasiado pequeña")
                .hasMessageContaining("1079×1350").hasMessageContaining("1080×1350");
        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, imagen(1080, 1349, "png"), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("demasiado pequeña");

        verify(dao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void demasiadoPesadaSeRechaza() {

        byte[] enorme = new byte[5 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, enorme, "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La imagen pesa más de 5 MB");

        verify(dao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void exactamenteCincoMegasAunSePermiteEnPeso() {

        // Un archivo de 5 MB que no es una imagen falla por ser inválido, no por el peso.
        byte[] cinco = new byte[5 * 1024 * 1024];

        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, cinco, "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es una imagen válida");
    }

    @Test
    void otroTipoDeArchivoSeRechaza() throws Exception {

        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, imagen(1200, 1500, "gif"), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Solo se admiten imágenes JPG o PNG");
        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, "no soy una imagen".getBytes(), "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es una imagen válida");
        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, new byte[0], "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No se ha recibido ningún archivo");
        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dao, never()).guardar(anyString(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void unPngTruncadoSeRechaza() throws Exception {

        byte[] png = imagen(1200, 1500, "png");
        byte[] truncado = java.util.Arrays.copyOf(png, png.length / 3);

        assertThatThrownBy(() -> servicio.subir(CartelRedes.PRIMER_EQUIPO, truncado, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elFilialSePuedeSubirComoElPrimerEquipo() throws Exception {

        byte[] png = imagen(1200, 1500, "png");

        servicio.subir(CartelRedes.FILIAL, png, "admin");

        verify(dao).guardar("FILIAL", png, "image/png", 1200, 1500, "admin");
        verify(dao, never()).guardar(eq("PRIMER_EQUIPO"), any(), anyString(), anyInt(), anyInt(), any());
    }

    // ---- el fondo vigente --------------------------------------------------------------------------------

    @Test
    void sinFilaEnLaTablaSeUsaElFondoPorDefectoDeResources() {

        Fondo fondo = servicio.actual(CartelRedes.PRIMER_EQUIPO);

        assertThat(fondo.personalizado()).isFalse();
        assertThat(fondo.tipoContenido()).isEqualTo("image/jpeg");
        assertThat(fondo.ancho()).isEqualTo(1075);
        assertThat(fondo.alto()).isEqualTo(1345);
        assertThat(fondo.bytes()).isNotEmpty();
        assertThat(fondo.fecha()).isNull();
    }

    @Test
    void conFilaEnLaTablaSeUsaEseFondoYNoElPorDefecto() {

        LocalDateTime fecha = LocalDateTime.of(2026, 11, 19, 10, 0);
        when(dao.obtener("PRIMER_EQUIPO"))
                .thenReturn(new FondoCartel("PRIMER_EQUIPO", new byte[] { 7, 7 }, "image/png", 1500, 1900, fecha, "admin"));

        Fondo fondo = servicio.actual(CartelRedes.PRIMER_EQUIPO);

        assertThat(fondo.personalizado()).isTrue();
        assertThat(fondo.bytes()).containsExactly(7, 7);
        assertThat(fondo.ancho()).isEqualTo(1500);
        assertThat(fondo.alto()).isEqualTo(1900);
        assertThat(fondo.fecha()).isEqualTo(fecha);
        assertThat(fondo.usuario()).isEqualTo("admin");
    }

    @Test
    void restaurarBorraLaFilaYVuelveElPorDefecto() {

        when(dao.borrar("PRIMER_EQUIPO")).thenReturn(true);

        assertThat(servicio.restaurar(CartelRedes.PRIMER_EQUIPO)).isTrue();

        verify(dao).borrar("PRIMER_EQUIPO");
        // Ya sin fila (el mock devuelve null), vuelve el de por defecto.
        assertThat(servicio.actual(CartelRedes.PRIMER_EQUIPO).personalizado()).isFalse();
    }

    @Test
    void elFilialTieneSuPropioFondoPorDefectoYRestaurarLoBorra() {

        var fondo = servicio.actual(CartelRedes.FILIAL);

        assertThat(fondo.personalizado()).isFalse();
        assertThat(fondo.tipoContenido()).isEqualTo("image/jpeg");
        assertThat(fondo.ancho()).isEqualTo(1079);
        assertThat(fondo.alto()).isEqualTo(1350);
        verify(dao).obtener(eq("FILIAL"));

        servicio.restaurar(CartelRedes.FILIAL);

        verify(dao).borrar("FILIAL");
    }
}
