package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.util.Date;

import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.model.TemporadaDTO;

/**
 * Fechas de una temporada para filtrar listados en memoria. Sin temporada (o sin alguna de sus fechas) ese
 * extremo no filtra, y un dato sin fecha siempre se conserva.
 */
record RangoTemporada(LocalDate desde, LocalDate hasta) {

    static final RangoTemporada SIN_FILTRO = new RangoTemporada(null, null);

    static RangoTemporada de(Temporada temporada) {
        return temporada == null ? SIN_FILTRO
                : new RangoTemporada(aLocalDate(temporada.getFechaInicio()), aLocalDate(temporada.getFechaFin()));
    }

    static RangoTemporada de(TemporadaDTO temporada) {
        return temporada == null ? SIN_FILTRO
                : new RangoTemporada(aLocalDate(temporada.getFechaInicio()), aLocalDate(temporada.getFechaFin()));
    }

    boolean contiene(LocalDate fecha) {
        return fecha == null || ((desde == null || !fecha.isBefore(desde)) && (hasta == null || !fecha.isAfter(hasta)));
    }

    boolean contiene(Date fecha) {
        return contiene(aLocalDate(fecha));
    }

    // java.sql.Date no admite toInstant().
    private static LocalDate aLocalDate(Date fecha) {
        if (fecha == null) {
            return null;
        }

        return fecha instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                : fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}
