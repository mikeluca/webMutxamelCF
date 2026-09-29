package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;
import com.mikedev.mutxamelcf.service.JwtService;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;
import com.mikedev.mutxamelcf.util.TokenUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UsuarioAppServiceImpl implements UsuarioAppService {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioAppServiceImpl.class);

    private static final long HORAS_VALIDEZ_TOKEN = 4;

    private static final int MAX_INTENTOS_ACTIVACION = 5;

    /*
     * SEC-02: un único mensaje para cualquier motivo por el que la
     * activación no puede continuar (email sin invitación, código
     * incorrecto, código caducado, intentos agotados...). Antes había
     * mensajes distintos para cada caso, lo que permitía enumerar qué
     * emails tenían una invitación pendiente.
     */
    private static final String MENSAJE_ACTIVACION_INVALIDA = "El código no es válido o ha caducado.";

    /*
     * SEC-06: un único mensaje para email inexistente, cuenta inactiva
     * (invitación aún no canjeada) o contraseña incorrecta. Antes la
     * cuenta inactiva devolvía "La cuenta no está activa" antes de
     * comprobar la contraseña, lo que permitía enumerar qué emails
     * tenían una invitación pendiente; además ese caso no contaba como
     * intento fallido a efectos de rate limiting.
     */
    private static final String MENSAJE_LOGIN_INVALIDO = "Email o contraseña incorrectos";

    /*
     * Hash BCrypt de un valor fijo que no es la contraseña de nadie.
     * Se compara contra él cuando el usuario no existe (o no tiene aún
     * contraseña) para que passwordEncoder.matches() tarde lo mismo que
     * con un usuario real y no se pueda distinguir por tiempos si un
     * email está o no registrado.
     */
    private static final String HASH_FICTICIO_PARA_TIMING = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final Set<String> TIPOS_VINCULO_CON_PERSONA = Set.of(
            "JUGADOR", "FAMILIAR", "ENTRENADOR");

    /*
     * Roles de club sin vínculo a una persona concreta (a diferencia de
     * jugador/familiar/entrenador, que sí atan la cuenta a una ficha).
     */
    private static final Set<String> TIPOS_VINCULO_SIN_PERSONA = Set.of(
            "COORDINADOR", "RETRANSMISION");

    private final UsuarioAppDao usuarioAppDao;
    private final UsuarioAppVinculoDao usuarioAppVinculoDao;
    private final RolAppDao rolAppDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UsuarioAppServiceImpl(
            UsuarioAppDao usuarioAppDao,
            UsuarioAppVinculoDao usuarioAppVinculoDao,
            RolAppDao rolAppDao,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.usuarioAppDao = usuarioAppDao;
        this.usuarioAppVinculoDao = usuarioAppVinculoDao;
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

        String codigo = TokenUtils.generarCodigoActivacion();

        String codigoHash = TokenUtils.hashToken(codigo);

        Timestamp expiracion = Timestamp.from(
                Instant.now().plus(HORAS_VALIDEZ_TOKEN, ChronoUnit.HOURS));

        usuarioAppDao.actualizarTokenActivacion(
                usuarioId,
                codigoHash,
                expiracion);

        /*
         * Devolvemos el código original.
         *
         * Este código será el que posteriormente
         * enviaremos al usuario por email.
         */
        return codigo;
    }

    @Override
    @Transactional(noRollbackFor = { IllegalArgumentException.class, IllegalStateException.class })
    public UsuarioApp activarCuenta(
            String email,
            String codigo,
            String password) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "El email es obligatorio");
        }

        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(
                    "El código es obligatorio");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 8 caracteres");
        }

        UsuarioApp usuario = obtenerPorEmail(email);

        if (usuario == null
                || usuario.getTokenActivacion() == null) {

            throw new IllegalArgumentException(
                    MENSAJE_ACTIVACION_INVALIDA);
        }

        if (usuario.isActivo()) {
            throw new IllegalStateException(
                    "La cuenta ya está activa");
        }

        /*
         * SEC-02: consumo atómico y condicionado del intento (incluye
         * el chequeo de caducidad, del máximo de intentos y de que
         * siga pendiente de activar). @Transactional(noRollbackFor=...)
         * de arriba asegura que este UPDATE se conserva aunque el
         * método termine lanzando una excepción más abajo; y al
         * comprobar la condición dentro de la propia sentencia SQL, dos
         * peticiones concurrentes con el mismo código no pueden leer
         * ambas el contador "antiguo" y colarse las dos por debajo del
         * límite.
         */
        int filas = usuarioAppDao.consumirIntentoActivacion(
                usuario.getId(),
                MAX_INTENTOS_ACTIVACION);

        if (filas == 0) {
            throw new IllegalArgumentException(
                    MENSAJE_ACTIVACION_INVALIDA);
        }

        String codigoHash = TokenUtils.hashToken(codigo.trim());

        boolean coincide = MessageDigest.isEqual(
                codigoHash.getBytes(StandardCharsets.UTF_8),
                usuario.getTokenActivacion().getBytes(StandardCharsets.UTF_8));

        if (!coincide) {
            throw new IllegalArgumentException(
                    MENSAJE_ACTIVACION_INVALIDA);
        }

        String passwordHash = passwordEncoder.encode(password);

        usuarioAppDao.actualizarPassword(
                usuario.getId(),
                passwordHash);

        usuarioAppDao.activarUsuario(
                usuario.getId());

        return usuario;
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

        String hashComparar = (usuario != null && usuario.getPasswordHash() != null)
                ? usuario.getPasswordHash()
                : HASH_FICTICIO_PARA_TIMING;

        // Se compara siempre, exista o no el usuario, para que el tiempo
        // de respuesta no permita distinguir un email no registrado.
        boolean passwordValida = passwordEncoder.matches(password, hashComparar);

        if (usuario == null || !usuario.isActivo() || !passwordValida) {
            throw new IllegalArgumentException(MENSAJE_LOGIN_INVALIDO);
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

    /*
     * ADMINISTRACIÓN DESDE LA WEB (panel SUPER).
     */

    @Override
    public List<UsuarioAppAdminResponse> listarUsuariosAdmin() {

        List<UsuarioApp> usuarios = usuarioAppDao.listarTodos();

        List<UsuarioAppAdminResponse> respuesta = new ArrayList<>();

        for (UsuarioApp usuario : usuarios) {
            respuesta.add(construirRespuestaAdmin(usuario));
        }

        return respuesta;
    }

    @Override
    public PersonasVinculablesResponse obtenerPersonasVinculables() {

        return new PersonasVinculablesResponse(
                usuarioAppVinculoDao.obtenerJugadoresSinCuenta(),
                usuarioAppVinculoDao.obtenerFamiliaresSinCuenta(),
                usuarioAppVinculoDao.obtenerCuerpoTecnicoSinCuenta());
    }

    @Override
    @Transactional
    public InvitacionUsuarioApp invitarUsuario(InvitarUsuarioAppRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "La petición no puede ser nula");
        }

        List<VinculoSolicitado> vinculos = normalizarVinculos(request.getVinculos());

        /*
         * Validamos todo (personas sin cuenta ya, roles configurados)
         * antes de crear nada: así una petición inválida no deja a
         * medias una cuenta creada sin sus vínculos.
         */
        List<VinculoResuelto> resueltos = validarVinculos(vinculos);

        /*
         * El email de un familiar SIEMPRE sale de su ficha (FAMILIARES.EMAIL),
         * nunca de lo que escriba OFICINA en el formulario: así solo se puede
         * cambiar editando al familiar, y no hay forma de que la invitación
         * acabe en un email distinto al de contacto real de esa persona. Si
         * hay varios vínculos a la vez y uno es FAMILIAR, manda ese email.
         */
        VinculoResuelto familiar = resueltos.stream()
                .filter(v -> "FAMILIAR".equals(v.tipo()))
                .findFirst()
                .orElse(null);

        String email = familiar != null
                ? emailFamiliarObligatorio(familiar.personaId())
                : validarEmailEscrito(request.getEmail());

        UsuarioApp usuario = new UsuarioApp();
        usuario.setEmail(email);

        int usuarioAppId = crearUsuario(usuario);

        String nombrePersona = aplicarVinculosResueltos(usuarioAppId, resueltos);

        String token = generarTokenActivacion(usuarioAppId);

        logger.info(
                "Invitación de app creada: usuarioAppId={}, tipos={}",
                usuarioAppId,
                vinculos.stream().map(VinculoSolicitado::getTipo).toList());

        return new InvitacionUsuarioApp(
                usuarioAppId,
                usuario.getEmail(),
                nombrePersona,
                token);
    }

    @Override
    @Transactional
    public void agregarVinculosAUsuarioExistente(
            int usuarioAppId,
            AnadirVinculosRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "La petición no puede ser nula");
        }

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioAppId);

        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        List<VinculoSolicitado> vinculos = normalizarVinculos(request.getVinculos());
        List<VinculoResuelto> resueltos = validarVinculos(vinculos);

        aplicarVinculosResueltos(usuarioAppId, resueltos);

        logger.info(
                "Vínculos añadidos a usuario de la app: usuarioAppId={}, tipos={}",
                usuarioAppId,
                vinculos.stream().map(VinculoSolicitado::getTipo).toList());
    }

    @Override
    @Transactional
    public void quitarVinculo(int usuarioAppId, VinculoSolicitado vinculo) {

        if (vinculo == null) {
            throw new IllegalArgumentException(
                    "El vínculo a quitar es obligatorio");
        }

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioAppId);

        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        String tipo = normalizarTipo(vinculo.getTipo());

        if (TIPOS_VINCULO_CON_PERSONA.contains(tipo) && vinculo.getPersonaId() == null) {
            throw new IllegalArgumentException(
                    "Falta la persona del vínculo a quitar");
        }

        switch (tipo) {
            case "JUGADOR" -> usuarioAppVinculoDao.desvincularJugador(
                    usuarioAppId, vinculo.getPersonaId());
            case "FAMILIAR" -> usuarioAppVinculoDao.desvincularFamiliar(
                    usuarioAppId, vinculo.getPersonaId());
            case "ENTRENADOR" -> usuarioAppVinculoDao.desvincularCuerpoTecnico(
                    usuarioAppId, vinculo.getPersonaId());
            case "COORDINADOR", "RETRANSMISION" -> {
                /* Sin vínculo a persona: solo se quita el rol, abajo. */
            }
            default -> throw new IllegalArgumentException(
                    "Tipo de vínculo no válido: " + vinculo.getTipo());
        }

        /*
         * El rol asociado solo se quita si, tras este borrado, la cuenta
         * no conserva ningún otro vínculo del mismo tipo (un entrenador
         * de 2 equipos no debe perder el rol ENTRENADOR al quitarle uno).
         */
        boolean quedaOtroDelMismoTipo = TIPOS_VINCULO_CON_PERSONA.contains(tipo)
                && usuarioAppVinculoDao.obtenerVinculos(usuarioAppId).stream()
                        .anyMatch(v -> tipo.equals(v.getTipo()));

        if (!quedaOtroDelMismoTipo) {

            RolApp rol = obtenerRolPorCodigo(tipo);

            if (rol != null) {
                eliminarRol(usuarioAppId, rol.getId());
            }
        }

        logger.info(
                "Vínculo quitado de usuario de la app: usuarioAppId={}, tipo={}",
                usuarioAppId,
                tipo);
    }

    /**
     * Un vínculo ya validado: persona confirmada sin cuenta previa (si
     * aplica), su nombre resuelto y el rol de club correspondiente ya
     * localizado en ROLES_APP. Separar validación de aplicación permite
     * comprobar toda la petición (incluida la de invitar, antes de que
     * exista la cuenta) sin dejar nada a medias si algo no es válido.
     */
    private record VinculoResuelto(String tipo, Long personaId, int rolId, String nombre) {
    }

    private List<VinculoResuelto> validarVinculos(List<VinculoSolicitado> vinculos) {

        List<VinculoResuelto> resueltos = new ArrayList<>();

        for (VinculoSolicitado vinculo : vinculos) {

            String tipo = normalizarTipo(vinculo.getTipo());
            String nombre = null;

            if (TIPOS_VINCULO_CON_PERSONA.contains(tipo)) {

                if (vinculo.getPersonaId() == null) {
                    throw new IllegalArgumentException(
                            "Debes seleccionar a la persona a vincular");
                }

                validarPersonaSinCuenta(tipo, vinculo.getPersonaId());

                nombre = usuarioAppVinculoDao.obtenerNombrePersona(
                        tipo,
                        vinculo.getPersonaId());

                if (nombre == null) {
                    throw new IllegalArgumentException(
                            "La persona seleccionada no existe");
                }

            } else if (!TIPOS_VINCULO_SIN_PERSONA.contains(tipo)) {

                throw new IllegalArgumentException(
                        "Tipo de vínculo no válido: " + vinculo.getTipo());
            }

            RolApp rol = obtenerRolPorCodigo(tipo);

            if (rol == null) {
                throw new IllegalStateException(
                        "El rol " + tipo + " no está configurado en ROLES_APP");
            }

            resueltos.add(new VinculoResuelto(tipo, vinculo.getPersonaId(), rol.getId(), nombre));
        }

        return resueltos;
    }

    /**
     * Inserta el vínculo (si aplica) y asigna el rol de cada elemento
     * de la lista, ya validada por {@link #validarVinculos}. Devuelve
     * el nombre de la primera persona vinculada (misma persona real
     * independientemente de cuántos vínculos tenga), o null si ninguno
     * de los vínculos tenía persona (COORDINADOR/RETRANSMISION).
     */
    private String aplicarVinculosResueltos(int usuarioAppId, List<VinculoResuelto> resueltos) {

        String nombrePersona = null;

        for (VinculoResuelto vinculo : resueltos) {

            switch (vinculo.tipo()) {
                case "JUGADOR" -> usuarioAppVinculoDao.vincularJugador(
                        usuarioAppId, vinculo.personaId());
                case "FAMILIAR" -> usuarioAppVinculoDao.vincularFamiliar(
                        usuarioAppId, vinculo.personaId());
                case "ENTRENADOR" -> usuarioAppVinculoDao.vincularCuerpoTecnico(
                        usuarioAppId, vinculo.personaId());
                default -> {
                    /* COORDINADOR: rol de club, sin vínculo con una persona. */
                }
            }

            asignarRol(usuarioAppId, vinculo.rolId());

            if (nombrePersona == null && vinculo.nombre() != null) {
                nombrePersona = vinculo.nombre();
            }
        }

        return nombrePersona;
    }

    private List<VinculoSolicitado> normalizarVinculos(List<VinculoSolicitado> vinculos) {

        if (vinculos == null || vinculos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debes seleccionar al menos un rol/vínculo");
        }

        return vinculos;
    }

    @Override
    public InvitacionUsuarioApp reenviarInvitacion(int usuarioAppId) {

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioAppId);

        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        if (usuario.isActivo()) {
            throw new IllegalStateException("La cuenta ya está activa");
        }

        List<VinculoUsuarioApp> vinculos = usuarioAppVinculoDao.obtenerVinculos(usuarioAppId);

        String token = generarTokenActivacion(usuarioAppId);

        logger.info("Invitación de app reenviada: usuarioAppId={}", usuarioAppId);

        return new InvitacionUsuarioApp(
                usuarioAppId,
                usuario.getEmail(),
                vinculos.isEmpty() ? null : vinculos.get(0).getNombreCompleto(),
                token);
    }

    @Override
    public void activarUsuarioAdmin(int usuarioAppId) {

        if (usuarioAppDao.obtenerPorId(usuarioAppId) == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        usuarioAppDao.activarUsuario(usuarioAppId);

        logger.info("Cuenta de app activada manualmente: usuarioAppId={}", usuarioAppId);
    }

    @Override
    public void desactivarUsuarioAdmin(int usuarioAppId) {

        if (usuarioAppDao.obtenerPorId(usuarioAppId) == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        usuarioAppDao.desactivarUsuario(usuarioAppId);

        logger.info("Cuenta de app desactivada: usuarioAppId={}", usuarioAppId);
    }

    @Override
    @Transactional
    public void eliminarInvitacion(int usuarioAppId) {

        UsuarioApp usuario = usuarioAppDao.obtenerPorId(usuarioAppId);

        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        if (usuario.isActivo()) {
            throw new IllegalStateException(
                    "No se puede eliminar una cuenta ya activa. "
                            + "Desactívala si quieres revocarle el acceso.");
        }

        usuarioAppVinculoDao.desvincularTodo(usuarioAppId);
        rolAppDao.eliminarTodosLosRoles(usuarioAppId);
        usuarioAppDao.eliminar(usuarioAppId);

        // Privacidad: se registra el id, no el email.
        logger.info("Invitación de app eliminada: usuarioAppId={}", usuarioAppId);
    }

    private String emailFamiliarObligatorio(Long familiarId) {

        String email = usuarioAppVinculoDao.obtenerEmailFamiliar(familiarId);

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Este familiar no tiene un email registrado. "
                            + "Añádelo primero en Familiares antes de invitarlo a la app.");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String validarEmailEscrito(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }

        String normalizado = email.trim();

        if (!normalizado.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }

        return normalizado;
    }

    private void validarPersonaSinCuenta(String tipo, Long personaId) {

        boolean tieneCuenta = switch (tipo) {
            case "JUGADOR" -> usuarioAppVinculoDao.jugadorTieneCuenta(personaId);
            case "FAMILIAR" -> usuarioAppVinculoDao.familiarTieneCuenta(personaId);
            case "ENTRENADOR" -> usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(personaId);
            default -> false;
        };

        if (tieneCuenta) {
            throw new IllegalArgumentException(
                    "Esa persona ya tiene una cuenta de la app");
        }
    }

    private String normalizarTipo(String tipoVinculo) {

        if (tipoVinculo == null || tipoVinculo.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo de vínculo es obligatorio");
        }

        return tipoVinculo.trim().toUpperCase(Locale.ROOT);
    }

    private UsuarioAppAdminResponse construirRespuestaAdmin(UsuarioApp usuario) {

        UsuarioAppAdminResponse response = new UsuarioAppAdminResponse();

        response.setId(usuario.getId());
        response.setEmail(usuario.getEmail());
        response.setActivo(usuario.isActivo());
        response.setFechaAlta(usuario.getFechaAlta());
        response.setFechaActivacion(usuario.getFechaActivacion());
        response.setFechaUltimoAcceso(usuario.getFechaUltimoAcceso());

        boolean tokenPendiente = !usuario.isActivo()
                && usuario.getTokenActivacion() != null;

        response.setTokenPendiente(tokenPendiente);

        boolean tokenExpirado = tokenPendiente
                && usuario.getFechaExpiracionToken() != null
                && usuario.getFechaExpiracionToken().before(
                        Timestamp.from(Instant.now()));

        response.setTokenExpirado(tokenExpirado);

        List<RolApp> roles = obtenerRoles(usuario.getId());

        response.setRoles(
                roles.stream()
                        .map(RolApp::getCodigo)
                        .toList());

        response.setVinculos(usuarioAppVinculoDao.obtenerVinculos(usuario.getId()));

        return response;
    }
}