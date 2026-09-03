package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.util.TokenUtils;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import java.util.List;

@Service
public class UsuarioAppServiceImpl implements UsuarioAppService {

    private final UsuarioAppDao usuarioAppDao;
    private final RolAppDao rolAppDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UsuarioAppServiceImpl(
            UsuarioAppDao usuarioAppDao,
            RolAppDao rolAppDao,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.usuarioAppDao = usuarioAppDao;
        this.rolAppDao = rolAppDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UsuarioApp obtenerPorEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return usuarioAppDao.obtenerPorEmail(
                email.trim().toLowerCase());
    }

    @Override
    public UsuarioApp obtenerPorId(int id) {
        return usuarioAppDao.obtenerPorId(id);
    }

    @Override
    public UsuarioApp obtenerPorTokenActivacion(String token) {

        if (token == null || token.isBlank()) {
            return null;
        }

        String tokenHash = TokenUtils.hashToken(token);

        return usuarioAppDao.obtenerPorTokenActivacion(
                tokenHash);
    }

    @Override
    public int crearUsuario(UsuarioApp usuario) {

        if (usuario == null) {
            throw new IllegalArgumentException(
                    "El usuario no puede ser null");
        }

        if (usuario.getEmail() == null
                || usuario.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "El email es obligatorio");
        }

        String email = usuario.getEmail()
                .trim()
                .toLowerCase();

        UsuarioApp existente = usuarioAppDao.obtenerPorEmail(email);

        if (existente != null) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con ese email");
        }

        usuario.setEmail(email);

        /*
         * Los usuarios creados mediante invitación
         * comienzan inactivos y sin contraseña.
         */
        usuario.setActivo(false);
        usuario.setPasswordHash(null);

        return usuarioAppDao.guardar(usuario);
    }

    @Override
    public String generarTokenActivacion(int usuarioId) {

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioId);

        if (usuario == null) {
            throw new IllegalArgumentException(
                    "El usuario no existe");
        }

        if (usuario.isActivo()) {
            throw new IllegalStateException(
                    "La cuenta ya está activa");
        }

        String token = TokenUtils.generarToken();

        String tokenHash = TokenUtils.hashToken(token);

        usuarioAppDao.actualizarTokenActivacion(
                usuarioId,
                tokenHash);

        /*
         * Devolvemos el token original.
         *
         * Este token será el que posteriormente
         * enviaremos al usuario por email.
         */
        return token;
    }

    @Override
    public void activarCuenta(
            String token,
            String password) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "El token es obligatorio");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 8 caracteres");
        }

        UsuarioApp usuario = obtenerPorTokenActivacion(token);

        if (usuario == null) {
            throw new IllegalArgumentException(
                    "El token de activación no es válido");
        }

        if (usuario.isActivo()) {
            throw new IllegalStateException(
                    "La cuenta ya está activa");
        }

        String passwordHash = passwordEncoder.encode(password);

        usuarioAppDao.actualizarPassword(
                usuario.getId(),
                passwordHash);

        usuarioAppDao.activarUsuario(
                usuario.getId());
    }

    @Override
    public LoginAppResponse login(
            String email,
            String password) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "El email es obligatorio");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "La contraseña es obligatoria");
        }

        UsuarioApp usuario = obtenerPorEmail(email);

        if (usuario == null) {
            throw new IllegalArgumentException(
                    "Email o contraseña incorrectos");
        }

        if (!usuario.isActivo()) {
            throw new IllegalStateException(
                    "La cuenta no está activa");
        }

        if (usuario.getPasswordHash() == null
                || !passwordEncoder.matches(
                        password,
                        usuario.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Email o contraseña incorrectos");
        }

        List<RolApp> roles = rolAppDao.obtenerPorUsuario(
                usuario.getId());

        String token = jwtService.generarToken(
                usuario.getId(),
                usuario.getEmail(),
                roles);

        usuarioAppDao.actualizarUltimoAcceso(
                usuario.getId());

        List<String> codigosRoles = roles.stream()
                .map(RolApp::getCodigo)
                .toList();

        return new LoginAppResponse(
                token,
                usuario.getId(),
                usuario.getEmail(),
                codigosRoles);
    }

    @Override
    public void actualizarUltimoAcceso(int id) {
        usuarioAppDao.actualizarUltimoAcceso(id);
    }

    @Override
    public List<RolApp> obtenerRoles(int usuarioAppId) {
        return rolAppDao.obtenerPorUsuario(usuarioAppId);
    }

    @Override
    public RolApp obtenerRolPorCodigo(String codigo) {

        if (codigo == null || codigo.isBlank()) {
            return null;
        }

        return rolAppDao.obtenerPorCodigo(
                codigo.trim().toUpperCase());
    }

    @Override
    public boolean tieneRol(
            int usuarioAppId,
            String codigoRol) {

        if (codigoRol == null || codigoRol.isBlank()) {
            return false;
        }

        List<RolApp> roles = rolAppDao.obtenerPorUsuario(
                usuarioAppId);

        return roles.stream()
                .anyMatch(rol -> codigoRol.equalsIgnoreCase(
                        rol.getCodigo()));
    }

    @Override
    public void asignarRol(
            int usuarioAppId,
            int rolId) {

        List<RolApp> roles = rolAppDao.obtenerPorUsuario(
                usuarioAppId);

        boolean yaTieneRol = roles.stream()
                .anyMatch(rol -> rol.getId() == rolId);

        if (yaTieneRol) {
            return;
        }

        rolAppDao.asignarRol(
                usuarioAppId,
                rolId);
    }

    @Override
    public void eliminarRol(
            int usuarioAppId,
            int rolId) {

        rolAppDao.eliminarRol(
                usuarioAppId,
                rolId);
    }
}