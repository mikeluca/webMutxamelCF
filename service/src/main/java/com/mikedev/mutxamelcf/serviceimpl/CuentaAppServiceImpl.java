package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.dao.NotificacionAppDao;
import com.mikedev.mutxamelcf.dao.PreferenciasNotificacionDao;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.CuentaAppService;

@Service
public class CuentaAppServiceImpl implements CuentaAppService {

    private static final Logger logger = LoggerFactory.getLogger(CuentaAppServiceImpl.class);

    private static final String MENSAJE_PASSWORD_INCORRECTA = "La contraseña no es correcta";

    private final UsuarioAppDao usuarioAppDao;
    private final UsuarioAppVinculoDao usuarioAppVinculoDao;
    private final RolAppDao rolAppDao;
    private final DispositivoAppDao dispositivoAppDao;
    private final NotificacionAppDao notificacionAppDao;
    private final PreferenciasNotificacionDao preferenciasNotificacionDao;
    private final PasswordEncoder passwordEncoder;

    public CuentaAppServiceImpl(
            UsuarioAppDao usuarioAppDao,
            UsuarioAppVinculoDao usuarioAppVinculoDao,
            RolAppDao rolAppDao,
            DispositivoAppDao dispositivoAppDao,
            NotificacionAppDao notificacionAppDao,
            PreferenciasNotificacionDao preferenciasNotificacionDao,
            PasswordEncoder passwordEncoder) {

        this.usuarioAppDao = usuarioAppDao;
        this.usuarioAppVinculoDao = usuarioAppVinculoDao;
        this.rolAppDao = rolAppDao;
        this.dispositivoAppDao = dispositivoAppDao;
        this.notificacionAppDao = notificacionAppDao;
        this.preferenciasNotificacionDao = preferenciasNotificacionDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void eliminarCuenta(int usuarioAppId, String password) {

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioAppId);

        if (usuario == null || !usuario.isActivo() || usuario.isEliminada()) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        if (password == null
                || password.isBlank()
                || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new IllegalArgumentException(MENSAJE_PASSWORD_INCORRECTA);
        }

        Long id = (long) usuarioAppId;

        dispositivoAppDao.eliminarTodosDeUsuario(id);
        notificacionAppDao.eliminarTodasDeUsuario(id);
        preferenciasNotificacionDao.eliminarPorUsuario(id);
        usuarioAppVinculoDao.desvincularTodo(usuarioAppId);
        rolAppDao.eliminarTodosLosRoles(usuarioAppId);
        usuarioAppDao.anonimizar(usuarioAppId, emailAnonimo(usuarioAppId));

        // Privacidad: se registra el id, no el email.
        logger.info("Cuenta de app eliminada por su titular: usuarioAppId={}", usuarioAppId);
    }

    static String emailAnonimo(int usuarioAppId) {
        return "eliminada-" + usuarioAppId + "@cuenta-eliminada.invalid";
    }
}
