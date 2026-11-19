package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ImagenRedesDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.CartelRedes;
import com.mikedev.mutxamelcf.model.PartidoPrimerEquipoResponse;
import com.mikedev.mutxamelcf.service.CartelPrimerEquipoService;
import com.mikedev.mutxamelcf.service.FondosCartelesService;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Datos;

/** El cartel "Próximo partido del Primer Equipo": su equipo, su fondo y su renderizador; lo demás está en la base. */
@Service
public class CartelPrimerEquipoServiceImpl extends CartelPartidoServiceBase implements CartelPrimerEquipoService {

    static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";

    private final CartelPrimerEquipoRenderer renderer;

    public CartelPrimerEquipoServiceImpl(EquipoGestionDao equipoGestionDao, EquipoDao equipoDao, PartidoDao partidoDao,
            EquipoFfcvDao equipoFfcvDao, TemporadaDao temporadaDao, ImagenRedesDao imagenRedesDao,
            PartidoLiveEventoDao eventoDao, FondosCartelesService fondos, CartelPrimerEquipoRenderer renderer,
            Clock clock) {

        super(equipoGestionDao, equipoDao, partidoDao, equipoFfcvDao, temporadaDao, imagenRedesDao, eventoDao, fondos,
                clock, EQUIPO_PRIMER_EQUIPO, NOMBRE_MUTXAMEL, CartelRedes.PRIMER_EQUIPO);

        this.renderer = renderer;
    }

    @Override
    @Transactional(readOnly = true)
    public PartidoPrimerEquipoResponse partido(Long usuarioAppId) {
        return respuestaPartido(usuarioAppId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] png(Long usuarioAppId, String formato, String idioma, boolean racha, boolean qr, boolean hashtags) {

        Peticion peticion = prepararPng(usuarioAppId, formato, idioma, racha);
        Comun c = peticion.comun();
        boolean ca = c.ca();

        Datos datos = new Datos(c.local(), NOMBRE_MUTXAMEL.toUpperCase(Locale.ROOT), c.nombreRival(), c.escudoRival(),
                c.rivalPropio(), ca ? "PRÒXIM PARTIT" : "PRÓXIMO PARTIDO",
                c.jornada() == null ? null : "JORNADA " + c.jornada(), c.competicion().logo(),
                c.competicion().texto(), c.diaSemana(), c.fecha(), c.hora(), c.campoLineas(),
                ca ? "ÚLTIMS 5" : "ÚLTIMOS 5", c.racha(), hashtags, qr, ca ? "Descarrega l'app" : "Descarga la app");

        return renderer.renderizar(peticion.formato(), datos, fondo());
    }
}
