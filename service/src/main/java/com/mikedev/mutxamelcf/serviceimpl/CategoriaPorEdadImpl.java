package com.mikedev.mutxamelcf.serviceimpl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado.Estado;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;

@Service
public class CategoriaPorEdadImpl implements CategoriaPorEdad {

	private final CategoriaEdadDao categoriaEdadDao;

	public CategoriaPorEdadImpl(CategoriaEdadDao categoriaEdadDao) {
		this.categoriaEdadDao = categoriaEdadDao;
	}

	@Override
	public CategoriaPorEdadResultado categoriaPara(Integer anioNacimiento, Temporada temporada) {
		return categoriaPara(anioNacimiento, anioInicio(temporada));
	}

	@Override
	public CategoriaPorEdadResultado categoriaPara(Integer anioNacimiento, int anioInicioTemporada) {
		if (anioNacimiento == null) {
			return new CategoriaPorEdadResultado(null, Estado.SIN_ANIO_NACIMIENTO, null);
		}

		int edad = anioInicioTemporada - anioNacimiento;
		List<CategoriaEdad> categorias = categoriaEdadDao.obtenerTodas();

		if (categorias.isEmpty()) {
			return new CategoriaPorEdadResultado(null, Estado.SIN_CATEGORIA, edad);
		}

		// Si dos rangos se solapan manda el de menor ORDEN.
		CategoriaEdad coincidente = categorias.stream().filter(c -> c.contiene(edad))
				.min(Comparator.comparing(CategoriaEdad::getOrden, Comparator.nullsLast(Integer::compare)))
				.orElse(null);

		if (coincidente != null) {
			return new CategoriaPorEdadResultado(coincidente.getCategoria(), Estado.CATEGORIA, edad);
		}

		int maxima = categorias.stream().mapToInt(CategoriaEdad::getEdadMax).max().orElse(0);
		int minima = categorias.stream().mapToInt(CategoriaEdad::getEdadMin).min().orElse(0);

		if (edad > maxima) {
			return new CategoriaPorEdadResultado(null, Estado.SUPERA_EDAD_MAXIMA, edad);
		}
		if (edad < minima) {
			return new CategoriaPorEdadResultado(null, Estado.POR_DEBAJO_DEL_MINIMO, edad);
		}

		return new CategoriaPorEdadResultado(null, Estado.SIN_CATEGORIA, edad);
	}

	@Override
	public int anioInicio(Temporada temporada) {
		if (temporada == null || temporada.getFechaInicio() == null) {
			throw new IllegalArgumentException("La temporada no tiene fecha de inicio.");
		}
		// FECHA_INICIO llega como java.sql.Date o Timestamp según el origen; java.util.Date#toInstant no vale en el primero.
		return new java.sql.Date(temporada.getFechaInicio().getTime()).toLocalDate().getYear();
	}

	@Override
	public List<CategoriaEdad> obtenerTodas() {
		return categoriaEdadDao.obtenerTodas();
	}

	@Override
	public void guardar(CategoriaEdad c) {
		if (c == null || c.getCategoria() == null || c.getCategoria().isBlank()) {
			throw new IllegalArgumentException("La categoría es obligatoria.");
		}
		if (c.getEdadMin() == null || c.getEdadMax() == null || c.getOrden() == null) {
			throw new IllegalArgumentException("La edad mínima, la máxima y el orden son obligatorios.");
		}
		if (c.getEdadMin() < 0 || c.getEdadMax() < c.getEdadMin()) {
			throw new IllegalArgumentException("La edad máxima no puede ser menor que la mínima.");
		}

		c.setCategoria(c.getCategoria().trim());
		categoriaEdadDao.guardar(c);
	}

	@Override
	public void eliminar(String categoria) {
		categoriaEdadDao.eliminar(categoria);
	}
}
