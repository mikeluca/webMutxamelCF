package com.mikedev.mutxamelcf.serviceimpl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.ImagenRedesFondoDao;
import com.mikedev.mutxamelcf.model.CartelRedes;
import com.mikedev.mutxamelcf.model.FondoCartel;
import com.mikedev.mutxamelcf.service.FondosCartelesService;

@Service
public class FondosCartelesServiceImpl implements FondosCartelesService {

    // Contra imágenes que ocupan poco comprimidas y mucho en memoria.
    static final long MAXIMO_PIXELES = 25_000_000L;

    private final ImagenRedesFondoDao fondoDao;

    public FondosCartelesServiceImpl(ImagenRedesFondoDao fondoDao) {
        this.fondoDao = fondoDao;
    }

    @Override
    public Fondo actual(CartelRedes cartel) {

        FondoCartel guardado = fondoDao.obtener(cartel.name());

        if (guardado != null) {
            return new Fondo(cartel, guardado.imagen(), guardado.tipoContenido(), guardado.ancho(), guardado.alto(),
                    true, guardado.fechaActualizacion(), guardado.usuarioActualizo());
        }

        return porDefecto(cartel);
    }

    @Override
    public void subir(CartelRedes cartel, byte[] archivo, String usuario) {

        if (!cartel.disponible()) {
            throw new IllegalArgumentException("El cartel de " + cartel.nombre() + " aún no está disponible");
        }

        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException("No se ha recibido ningún archivo");
        }

        if (archivo.length > MAXIMO_BYTES) {
            throw new IllegalArgumentException("La imagen pesa más de 5 MB");
        }

        Leida leida = leer(archivo);

        if (leida.ancho < ANCHO_MINIMO || leida.alto < ALTO_MINIMO) {
            throw new IllegalArgumentException("La imagen es demasiado pequeña: mide " + leida.ancho + "×" + leida.alto
                    + " y el mínimo es " + ANCHO_MINIMO + "×" + ALTO_MINIMO + " píxeles");
        }

        fondoDao.guardar(cartel.name(), archivo, leida.tipoContenido, leida.ancho, leida.alto,
                usuario == null || usuario.isBlank() ? null : usuario.trim());
    }

    @Override
    public boolean restaurar(CartelRedes cartel) {
        return fondoDao.borrar(cartel.name());
    }

    // ---- utilidades ----------------------------------------------------------------------------

    private static Fondo porDefecto(CartelRedes cartel) {

        String recurso = cartel.fondoPorDefecto();

        if (recurso == null) {
            return null;
        }

        try (InputStream entrada = FondosCartelesServiceImpl.class.getResourceAsStream(recurso)) {

            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + recurso);
            }

            byte[] bytes = entrada.readAllBytes();
            Leida leida = leer(bytes);

            return new Fondo(cartel, bytes, leida.tipoContenido, leida.ancho, leida.alto, false, null, null);

        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer " + recurso, e);
        }
    }

    private record Leida(String tipoContenido, int ancho, int alto) {
    }

    /** Comprueba que es un JPG o PNG de verdad y completo, y devuelve su tipo y sus medidas. */
    private static Leida leer(byte[] archivo) {

        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(archivo))) {

            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);

            if (lectores == null || !lectores.hasNext()) {
                throw new IllegalArgumentException("El archivo no es una imagen válida (usa JPG o PNG)");
            }

            ImageReader lector = lectores.next();

            try {
                String formato = lector.getFormatName().toLowerCase();
                String tipo;

                if (formato.equals("png")) {
                    tipo = "image/png";
                } else if (formato.equals("jpeg") || formato.equals("jpg")) {
                    tipo = "image/jpeg";
                } else {
                    throw new IllegalArgumentException("Solo se admiten imágenes JPG o PNG");
                }

                lector.setInput(entrada, true, true);

                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);

                if ((long) ancho * alto > MAXIMO_PIXELES) {
                    throw new IllegalArgumentException(
                            "La imagen es demasiado grande: máximo 25 megapíxeles (la tuya mide " + ancho + "×" + alto
                                    + ")");
                }

                BufferedImage imagen = lector.read(0);

                if (imagen == null) {
                    throw new IllegalArgumentException("El archivo no es una imagen válida (usa JPG o PNG)");
                }

                return new Leida(tipo, ancho, alto);

            } finally {
                lector.dispose();
            }

        } catch (IOException | RuntimeException e) {

            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }

            throw new IllegalArgumentException("El archivo no es una imagen válida (usa JPG o PNG)");
        }
    }
}
