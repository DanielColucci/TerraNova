package com.hoteleria.gestion_hoteles.auth;

import com.hoteleria.gestion_hoteles.entity.Rol;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import com.hoteleria.gestion_hoteles.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registro, verificación de correo por ENLACE de un solo uso, inicio de sesión y administración de usuarios.
 * - El registro público SIEMPRE crea rol USUARIO (cualquier correo con formato válido).
 * - Solo se guarda el hash SHA-256 del token (vigencia 24 h). El token en claro viaja únicamente en el correo.
 * - No se revela si un correo existe: registro y reenvío responden igual exista o no la cuenta.
 */
@Service
public class AuthService {

    /** Token en claro (solo para armar el correo) junto con la cuenta. */
    public record Emitido(Usuario usuario, String token) { }

    private static final Pattern CORREO = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final long HORAS_TOKEN = 24;
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    /** Límite de frecuencia por correo (en memoria; no depende de que la cuenta exista). */
    private final Map<String, Instant> ultimoEnvio = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ registro público

    /** Crea la cuenta (USUARIO). Devuelve vacío si no hay que enviar correo (ya existe verificada o límite de frecuencia). */
    @Transactional
    public Optional<Emitido> registrar(String nombre, String correo, String password) {
        validarDatos(nombre, correo, password);
        String mail = normalizar(correo);
        Optional<Usuario> existente = usuarioRepository.findByCorreo(mail);
        if (existente.isPresent()) {
            Usuario u = existente.get();
            if (u.isVerified() || !u.isActivo() || !reservarEnvio(mail)) {
                return Optional.empty();   // misma respuesta pública: no se revela nada
            }
            return Optional.of(emitirToken(u));
        }
        Usuario u = new Usuario();
        u.setNombre(nombre.trim());
        u.setCorreo(mail);
        u.setPassword(passwordEncoder.encode(password));   // BCrypt
        u.setRol(Rol.USUARIO);
        u.setVerified(false);
        reservarEnvio(mail);
        return Optional.of(emitirToken(u));
    }

    /** Reenvío con límite de frecuencia (429). Responde igual exista o no la cuenta. */
    @Transactional
    public Optional<Emitido> reenviar(String correo) {
        if (correo == null || !CORREO.matcher(correo.trim()).matches()) {
            throw new AuthException(400, "Escribe un correo electrónico válido.");
        }
        String mail = normalizar(correo);
        long espera = segundosDeEspera(mail);
        if (espera > 0) {
            throw new AuthException(429, "Espera " + espera + " segundos antes de pedir otro correo.");
        }
        reservarEnvio(mail);
        Optional<Usuario> u = usuarioRepository.findByCorreo(mail);
        if (u.isEmpty() || u.get().isVerified() || !u.get().isActivo()) {
            return Optional.empty();
        }
        return Optional.of(emitirToken(u.get()));
    }

    // ------------------------------------------------------------------ verificación

    /** Consume el token (un solo uso). 400 si es inválido, usado o vencido. */
    @Transactional
    public Usuario verificar(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            throw new AuthException(400, "El enlace de verificación no es válido.");
        }
        Usuario u = usuarioRepository.findByTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new AuthException(400, "El enlace no es válido o ya fue utilizado."));
        if (u.getTokenExpira() == null || u.getTokenExpira().isBefore(LocalDateTime.now())) {
            throw new AuthException(400, "El enlace venció (dura 24 horas). Solicita un correo nuevo.");
        }
        u.setVerified(true);
        u.setTokenHash(null);
        u.setTokenExpira(null);
        return usuarioRepository.save(u);
    }

    @Transactional(readOnly = true)
    public Usuario autenticar(String correo, String password) {
        Usuario u = correo == null ? null : usuarioRepository.findByCorreo(normalizar(correo)).orElse(null);
        if (u == null || password == null || !passwordEncoder.matches(password, u.getPassword())) {
            throw new AuthException(401, "Correo o contraseña incorrectos.");
        }
        if (!u.isActivo()) {
            throw new AuthException(403, "Tu cuenta está desactivada. Contacta al administrador.");
        }
        if (!u.isVerified()) {
            throw new AuthException(403, "Debes verificar tu correo antes de iniciar sesión.", true);
        }
        return u;
    }

    // ------------------------------------------------------------------ administración de usuarios (solo ADMINISTRADOR)

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAllByOrderByFechaRegistroDesc();
    }

    /** Lista filtrada por rol (rol nulo = todos). */
    @Transactional(readOnly = true)
    public List<Usuario> listar(Rol rol) {
        return rol == null ? listar() : usuarioRepository.findByRolOrderByFechaRegistroDesc(rol);
    }

    /** Cambia el rol de una cuenta. No puedes cambiar el tuyo ni quitar al último administrador activo. */
    @Transactional
    public Usuario cambiarRol(Long id, Rol nuevo, Long idActor) {
        if (nuevo == null) {
            throw new AuthException(400, "Elige un rol válido.");
        }
        Usuario u = usuarioRepository.findById(id).orElseThrow(() -> new AuthException(404, "La cuenta no existe."));
        if (u.getIdUsuario().equals(idActor)) {
            throw new AuthException(400, "No puedes cambiar tu propio rol.");
        }
        if (u.getRol() == Rol.ADMINISTRADOR && nuevo != Rol.ADMINISTRADOR
                && u.isActivo() && usuarioRepository.countByRolAndActivoTrue(Rol.ADMINISTRADOR) <= 1) {
            throw new AuthException(400, "Debe quedar al menos un administrador activo.");
        }
        u.setRol(nuevo);
        return usuarioRepository.save(u);
    }

    /** El administrador crea cuentas ya verificadas (sin correo) con el rol que elija. */
    @Transactional
    public Usuario crearPorAdmin(String nombre, String correo, String password, Rol rol) {
        validarDatos(nombre, correo, password);
        String mail = normalizar(correo);
        if (usuarioRepository.existsByCorreo(mail)) {
            throw new AuthException(409, "Ya existe una cuenta con el correo " + mail + ".");
        }
        Usuario u = new Usuario();
        u.setNombre(nombre.trim());
        u.setCorreo(mail);
        u.setPassword(passwordEncoder.encode(password));
        u.setRol(rol == null ? Rol.USUARIO : rol);
        u.setVerified(true);
        return usuarioRepository.save(u);
    }

    @Transactional
    public Usuario cambiarActivo(Long id, boolean activo, Long idActor) {
        Usuario u = usuarioRepository.findById(id).orElseThrow(() -> new AuthException(404, "La cuenta no existe."));
        if (!activo && u.getIdUsuario().equals(idActor)) {
            throw new AuthException(400, "No puedes desactivar tu propia cuenta.");
        }
        u.setActivo(activo);
        return usuarioRepository.save(u);
    }

    // ------------------------------------------------------------------ internos

    private Emitido emitirToken(Usuario u) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        u.setTokenHash(sha256(token));
        u.setTokenExpira(LocalDateTime.now().plusHours(HORAS_TOKEN));
        return new Emitido(usuarioRepository.save(u), token);
    }

    private void validarDatos(String nombre, String correo, String password) {
        if (nombre == null || nombre.trim().length() < 2 || nombre.trim().length() > 120) {
            throw new AuthException(400, "El nombre es obligatorio (entre 2 y 120 caracteres).");
        }
        if (correo == null || !CORREO.matcher(correo.trim()).matches() || correo.trim().length() > 120) {
            throw new AuthException(400, "El correo electrónico no tiene un formato válido.");
        }
        if (password == null || password.length() < 8) {
            throw new AuthException(400, "La contraseña debe tener al menos 8 caracteres.");
        }
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private long segundosDeEspera(String mail) {
        Instant ultimo = ultimoEnvio.get(mail);
        if (ultimo == null) { return 0; }
        long restante = ESPERA_REENVIO.minus(Duration.between(ultimo, Instant.now())).getSeconds();
        return Math.max(0, restante);
    }

    /** true si se puede enviar ahora (y marca el envío). */
    private boolean reservarEnvio(String mail) {
        if (ultimoEnvio.size() > 5000) {
            Instant limite = Instant.now().minus(ESPERA_REENVIO);
            ultimoEnvio.values().removeIf(t -> t.isBefore(limite));
        }
        if (segundosDeEspera(mail) > 0) { return false; }
        ultimoEnvio.put(mail, Instant.now());
        return true;
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
