package com.mikedev.mutxamelcf.dao.impl;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.model.ImagenRedes;
import com.mikedev.mutxamelcf.model.PartidoImagen;

@Repository
public class ImagenRedesDaoImpl implements ImagenRedesDao {

	private final JdbcTemplate jdbcTemplate;

	public ImagenRedesDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<PartidoImagen> partidosEntre(LocalDate desde, LocalDate hasta) {
		long temporadaId = TemporadaActivaId.obtenerOMenosUno(jdbcTemplate);

		return jdbcTemplate.query("""
				SELECT P.ID, P.EQUIPO_ID, E.NOMBRE, E.ORDEN, COALESCE(ET.MODALIDAD, E.MODALIDAD) AS MODALIDAD,
				       P.RIVAL, P.CLUB_RIVAL_ID, P.DIA, P.HORA, P.CAMPO, P.ES_LOCAL, P.CANCELADO,
				       P.GOLES_FAVOR, P.GOLES_CONTRA, P.RESULTADO
				FROM PARTIDOS P
				JOIN EQUIPO E ON E.ID = P.EQUIPO_ID
				LEFT JOIN EQUIPO_TEMPORADA ET ON ET.EQUIPO_ID = E.ID AND ET.TEMPORADA_ID = ?
				WHERE P.DIA BETWEEN ? AND ?
				ORDER BY E.ORDEN, E.NOMBRE, P.DIA, P.ID
				""",
				(rs, n) -> {
					int esLocal = rs.getInt("ES_LOCAL");
					boolean local = rs.wasNull() || esLocal == 1;
					long club = rs.getLong("CLUB_RIVAL_ID");
					Long clubId = rs.wasNull() ? null : club;
					int favor = rs.getInt("GOLES_FAVOR");
					Integer golesFavor = rs.wasNull() ? null : favor;
					int contra = rs.getInt("GOLES_CONTRA");
					Integer golesContra = rs.wasNull() ? null : contra;

					return new PartidoImagen(
							rs.getLong("ID"), rs.getLong("EQUIPO_ID"), rs.getString("NOMBRE"), rs.getString("ORDEN"),
							rs.getString("MODALIDAD"), rs.getString("RIVAL"), clubId, rs.getDate("DIA").toLocalDate(),
							rs.getString("HORA"), rs.getString("CAMPO"), local, rs.getInt("CANCELADO") == 1,
							golesFavor, golesContra, rs.getString("RESULTADO"));
				},
				temporadaId, Date.valueOf(desde), Date.valueOf(hasta));
	}

	@Override
	public byte[] escudoRival(Long clubRivalId) {
		if (clubRivalId == null) {
			return null;
		}

		List<byte[]> escudos = jdbcTemplate.query("SELECT ESCUDO FROM CLUBES_RIVALES WHERE ID = ?",
				(rs, n) -> rs.getBytes(1), clubRivalId);

		return escudos.isEmpty() ? null : escudos.get(0);
	}

	@Override
	public void borrar(String tipo, LocalDate desde) {
		jdbcTemplate.update("DELETE FROM IMAGENES_REDES WHERE TIPO = ? AND FECHA_DESDE = ?", tipo, Date.valueOf(desde));
	}

	@Override
	public int versionMaxima(String tipo, LocalDate desde) {
		Integer version = jdbcTemplate.queryForObject(
				"SELECT MAX(VERSION) FROM IMAGENES_REDES WHERE TIPO = ? AND FECHA_DESDE = ?", Integer.class, tipo,
				Date.valueOf(desde));

		return version == null ? 0 : version;
	}

	@Override
	public void insertar(String tipo, String modalidad, int pagina, int totalPaginas, LocalDate desde, LocalDate hasta,
			byte[] png, LocalDateTime fechaGeneracion, int version) {

		jdbcTemplate.update("""
				INSERT INTO IMAGENES_REDES (TIPO, MODALIDAD, PAGINA, TOTAL_PAGINAS, FECHA_DESDE, FECHA_HASTA, PNG,
				                            FECHA_GENERACION, VERSION)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				ps -> {
					ps.setString(1, tipo);
					ps.setString(2, modalidad);
					ps.setInt(3, pagina);
					ps.setInt(4, totalPaginas);
					ps.setDate(5, Date.valueOf(desde));
					ps.setDate(6, Date.valueOf(hasta));
					ps.setBinaryStream(7, new ByteArrayInputStream(png), png.length);
					ps.setTimestamp(8, Timestamp.valueOf(fechaGeneracion));
					ps.setInt(9, version);
				});
	}

	@Override
	public List<ImagenRedes> listar(LocalDate desde) {
		return jdbcTemplate.query("""
				SELECT ID, TIPO, MODALIDAD, PAGINA, TOTAL_PAGINAS, FECHA_DESDE, FECHA_HASTA, FECHA_GENERACION, VERSION
				FROM IMAGENES_REDES
				WHERE FECHA_DESDE = ?
				ORDER BY TIPO DESC, MODALIDAD DESC, PAGINA
				""",
				(rs, n) -> new ImagenRedes(
						rs.getLong("ID"), rs.getString("TIPO"), rs.getString("MODALIDAD"), rs.getInt("PAGINA"),
						rs.getInt("TOTAL_PAGINAS"), rs.getDate("FECHA_DESDE").toLocalDate(),
						rs.getDate("FECHA_HASTA").toLocalDate(), rs.getTimestamp("FECHA_GENERACION").toLocalDateTime(),
						rs.getInt("VERSION")),
				Date.valueOf(desde));
	}

	@Override
	public byte[] obtenerPng(Long id) {
		List<byte[]> pngs = jdbcTemplate.query("SELECT PNG FROM IMAGENES_REDES WHERE ID = ?",
				(rs, n) -> rs.getBytes(1), id);

		return pngs.isEmpty() ? null : pngs.get(0);
	}
}
