package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;

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
import com.mikedev.mutxamelcf.service.CartelFilialService;
import com.mikedev.mutxamelcf.service.FondosCartelesService;
import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Datos;

/** El cartel "Próximo partido del Filial": su equipo ("Filial"), su fondo y su renderizador; lo demás está en la base. */
@Service
public class CartelFilialServiceImpl extends CartelPartidoServiceBase implements CartelFilialService {

    static final String EQUIPO_FILIAL = "Filial";

    /** El campo de casa en una sola línea. */
    static final String CAMPO_CASA = "CAMP NOU LOS OLMOS";

    /** Cómo se llama el equipo en el cartel y en el texto: Mutxamel C.F. “B” (comillas tipográficas). */
    static final String NOMBRE_FILIAL = "Mutxamel C.F. “B”";

    private final CartelFilialRenderer renderer;

    public CartelFilialServiceImpl(EquipoGestionDao equipoGestionDao, EquipoDao equipoDao, PartidoDao partidoDao,
            EquipoFfcvDao equipoFfcvDao, TemporadaDao temporadaDao, ImagenRedesDao imagenRedesDao,
            PartidoLiveEventoDao eventoDao, FondosCartelesService fondos, CartelFilialRenderer renderer,
            Clock clock) {

        super(equipoGestionDao, equipoDao, partidoDao, equipoFfcvDao, temporadaDao, imagenRedesDao, eventoDao, fondos,
                clock, EQUIPO_FILIAL, NOMBRE_FILIAL, CartelRedes.FILIAL);

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

        // El campo en una sola línea: "CAMP NOU LOS OLMOS" en casa; fuera, el campo del partido recortado.
        String campo = c.enCasaElsOms() ? CAMPO_CASA : c.campoLineas();

        Datos datos = new Datos(c.local(), NOMBRE_FILIAL.toUpperCase(java.util.Locale.ROOT), c.nombreRival(),
                c.escudoRival(), c.rivalPropio(), c.competicion().logo(), c.competicion().texto(),
                c.diaSemana() + " " + c.fecha(), c.hora(), campo, ca ? "ÚLTIMS 5" : "ÚLTIMOS 5", c.racha(), hashtags,
                qr, ca ? "Descarrega l'app" : "Descarga la app");

        return renderer.renderizar(peticion.formato(), datos, fondo());
    }
}
