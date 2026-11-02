package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import com.mikedev.mutxamelcf.dao.UsuarioAppDao;

/** Piezas comunes de los tests de los avisos: el catálogo real y el idioma de cada cuenta. */
final class AvisosTest {

    static final TextosAvisos TEXTOS = new TextosAvisos();

    private AvisosTest() {
    }

    /** Todas las cuentas en castellano (el valor por defecto). */
    static IdiomasUsuarios idiomas() {
        return idiomas(Map.of());
    }

    /** El idioma de cada cuenta; las que no salen aquí, castellano. */
    @SuppressWarnings("unchecked")
    static IdiomasUsuarios idiomas(Map<Long, String> porUsuario) {

        UsuarioAppDao dao = mock(UsuarioAppDao.class);

        lenient().when(dao.obtenerIdioma(anyLong())).thenAnswer(i -> porUsuario.getOrDefault(i.getArgument(0, Long.class), "es"));
        lenient().when(dao.obtenerIdiomas(anyCollection())).thenAnswer(i -> {
            Map<Long, String> idiomas = new HashMap<>();
            ((java.util.Collection<Long>) i.getArgument(0)).forEach(id -> idiomas.put(id, porUsuario.getOrDefault(id, "es")));
            return idiomas;
        });

        return new IdiomasUsuarios(dao);
    }
}
