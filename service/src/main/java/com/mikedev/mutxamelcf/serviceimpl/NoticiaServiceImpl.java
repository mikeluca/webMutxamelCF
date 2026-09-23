package com.mikedev.mutxamelcf.serviceimpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.NoticiaDao;
import com.mikedev.mutxamelcf.model.Noticia;
import com.mikedev.mutxamelcf.model.NoticiaAppDTO;
import com.mikedev.mutxamelcf.model.NoticiaDTO;
import com.mikedev.mutxamelcf.service.NoticiaService;

@Service
public class NoticiaServiceImpl implements NoticiaService {

	private static final Logger logger = LoggerFactory.getLogger(NoticiaServiceImpl.class);

	private final NoticiaDao noticiaDao;

	public NoticiaServiceImpl(NoticiaDao noticiaDao) {
		this.noticiaDao = noticiaDao;
	}

	@Override
	public boolean guardarNoticia(NoticiaDTO noticia) {
		logger.debug("Inicio guardarNoticia: titulo={}", noticia == null ? null : noticia.getTitulo());
		Noticia entidad = toEntity(noticia);
		boolean resultado = noticiaDao.guardarNoticia(entidad);

		// Propagamos el id generado (alta nueva) de vuelta al DTO
		// para que el llamador pueda, por ejemplo, referenciarlo en
		// la notificación push de "nueva noticia".
		if (resultado) {
			noticia.setId(entidad.getId());
		}

		logger.debug("Fin guardarNoticia: resultado={}", resultado);
		return resultado;
	}

	@Override
	public void eliminarNoticia(int id) {
		logger.debug("Inicio eliminarNoticia: id={}", id);
		noticiaDao.eliminarNoticia(id);
		logger.debug("Fin eliminarNoticia: id={}", id);
	}

	@Override
	public NoticiaDTO obtenerNoticiaPorId(int id) {
		logger.debug("Inicio obtenerNoticiaPorId: id={}", id);
		NoticiaDTO noticia = toDTO(noticiaDao.obtenerNoticiaPorId(id));
		logger.debug("Fin obtenerNoticiaPorId: id={}, encontrada={}", id, noticia != null);
		return noticia;
	}

	@Override
	public List<NoticiaDTO> obtenerNoticiasParaMostrar() {
		logger.debug("Inicio obtenerNoticiasParaMostrar");
		List<NoticiaDTO> noticias = toDTOList(noticiaDao.obtenerNoticiasParaMostrar());
		logger.debug("Fin obtenerNoticiasParaMostrar: total={}", noticias.size());
		return noticias;
	}

	@Override
	public List<NoticiaDTO> obtenerTodas() {
		logger.debug("Inicio obtenerTodas");
		List<NoticiaDTO> noticias = toDTOList(noticiaDao.obtenerTodas());
		logger.debug("Fin obtenerTodas: total={}", noticias.size());
		return noticias;
	}

	// Método para mapear Noticia a NoticiaDTO
	private static NoticiaDTO toDTO(Noticia noticia) {
		if (noticia == null) {
			return null;
		}

		NoticiaDTO noticiaDTO = new NoticiaDTO();
		noticiaDTO.setId(noticia.getId());
		noticiaDTO.setTitulo(noticia.getTitulo());
		noticiaDTO.setContenido(noticia.getContenido());
		noticiaDTO.setFecha(noticia.getFecha());
		noticiaDTO.setImagen(noticia.getImagen());

		if (noticiaDTO.getImagen() != null && noticiaDTO.getImagen().length > 0) {
			String imagenBase64 = Base64.getEncoder().encodeToString(noticiaDTO.getImagen());
			noticiaDTO.setImagenBase64(imagenBase64);
		} else {
			noticiaDTO.setImagenBase64(null);
		}

		return noticiaDTO;
	}

	// Método para mapear NoticiaDTO a Noticia
	private static Noticia toEntity(NoticiaDTO noticiaDTO) {
		if (noticiaDTO == null) {
			return null;
		}

		Noticia noticia = new Noticia();
		noticia.setId(noticiaDTO.getId());
		noticia.setTitulo(noticiaDTO.getTitulo());
		noticia.setContenido(noticiaDTO.getContenido());
		noticia.setFecha(noticiaDTO.getFecha());
		noticia.setImagen(noticiaDTO.getImagen());

		return noticia;
	}

	// Métodos para transformar listas de entidades a listas de DTOs
	private static List<NoticiaDTO> toDTOList(List<Noticia> noticias) {
		List<NoticiaDTO> listaNoticias = new ArrayList<NoticiaDTO>();
		for (Noticia j : noticias) {
			listaNoticias.add(toDTO(j));
		}
		return listaNoticias;
	}

	@Override
	public List<NoticiaAppDTO> obtenerNoticiasParaApp() {
		logger.debug("Inicio obtenerNoticiasParaApp");

		List<NoticiaAppDTO> noticias = toAppDTOList(
				noticiaDao.obtenerNoticiasParaMostrar());

		logger.debug(
				"Fin obtenerNoticiasParaApp: total={}",
				noticias.size());

		return noticias;
	}

	@Override
	public NoticiaAppDTO obtenerNoticiaParaApp(int id) {
		logger.debug("Inicio obtenerNoticiaParaApp: id={}", id);

		NoticiaAppDTO noticia = toAppDTO(
				noticiaDao.obtenerNoticiaPorId(id));

		logger.debug(
				"Fin obtenerNoticiaParaApp: id={}, encontrada={}",
				id,
				noticia != null);

		return noticia;
	}

	@Override
	public byte[] obtenerImagenNoticia(int id) {
		logger.debug("Inicio obtenerImagenNoticia: id={}", id);

		Noticia noticia = noticiaDao.obtenerNoticiaPorId(id);

		if (noticia == null) {
			return null;
		}

		return noticia.getImagen();
	}

	@Override
	public byte[] obtenerImagenNoticiaMini(int id) {
		logger.debug("Inicio obtenerImagenNoticiaMini: id={}", id);

		Noticia noticia = noticiaDao.obtenerNoticiaPorId(id);

		if (noticia == null || noticia.getImagen() == null) {
			return null;
		}

		try {
			BufferedImage imagenOriginal = ImageIO.read(
					new ByteArrayInputStream(noticia.getImagen()));

			if (imagenOriginal == null) {
				logger.warn(
						"No se pudo leer la imagen de la noticia: id={}",
						id);
				return null;
			}

			int anchoMaximo = 800;

			int anchoOriginal = imagenOriginal.getWidth();
			int altoOriginal = imagenOriginal.getHeight();

			int nuevoAncho;
			int nuevoAlto;

			if (anchoOriginal <= anchoMaximo) {
				nuevoAncho = anchoOriginal;
				nuevoAlto = altoOriginal;
			} else {
				nuevoAncho = anchoMaximo;
				nuevoAlto = (int) Math.round(
						altoOriginal * (double) nuevoAncho / anchoOriginal);
			}

			BufferedImage imagenMini = new BufferedImage(
					nuevoAncho,
					nuevoAlto,
					BufferedImage.TYPE_INT_RGB);

			Graphics2D graphics = imagenMini.createGraphics();

			graphics.drawImage(
					imagenOriginal,
					0,
					0,
					nuevoAncho,
					nuevoAlto,
					null);

			graphics.dispose();

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			ImageIO.write(
					imagenMini,
					"jpg",
					outputStream);

			byte[] resultado = outputStream.toByteArray();

			logger.debug(
					"Imagen miniatura generada: id={}, tamaño={}",
					id,
					resultado.length);

			return resultado;

		} catch (IOException e) {
			logger.error(
					"Error generando imagen miniatura: id={}",
					id,
					e);

			return null;
		}
	}

	private static NoticiaAppDTO toAppDTO(Noticia noticia) {

		if (noticia == null) {
			return null;
		}

		String imagenUrl = null;

		if (noticia.getImagen() != null &&
				noticia.getImagen().length > 0) {

			imagenUrl = "/api/public/noticias/"
					+ noticia.getId()
					+ "/imagen";
		}

		return new NoticiaAppDTO(
				noticia.getId(),
				noticia.getTitulo(),
				noticia.getContenido(),
				noticia.getFecha(),
				imagenUrl);
	}

	private static List<NoticiaAppDTO> toAppDTOList(
			List<Noticia> noticias) {

		List<NoticiaAppDTO> listaNoticias = new ArrayList<NoticiaAppDTO>();

		for (Noticia noticia : noticias) {
			listaNoticias.add(toAppDTO(noticia));
		}

		return listaNoticias;
	}

}
