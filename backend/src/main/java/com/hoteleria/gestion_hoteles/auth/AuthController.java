package com.hoteleria.gestion_hoteles.auth;

import com.hoteleria.gestion_hoteles.correo.EmailService;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import com.hoteleria.gestion_hoteles.seguridad.JwtAuthFilter;
import com.hoteleria.gestion_hoteles.seguridad.JwtService;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * API de autenticación:
 *   POST /api/auth/register     {nombre, correo, password}   -> 201 (respuesta genérica; el enlace llega por correo)
 *   POST /api/auth/verify       {token}                      -> cuenta verificada (token de un solo uso)
 *   POST /api/auth/resend-verification {correo}              -> reenvía el enlace (máx. 1 por minuto)
 *   POST /api/auth/login        {correo, password}           -> JWT en JSON y en cookie HttpOnly
 *   GET  /api/auth/me                                         -> usuario autenticado
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** true en producción (HTTPS): la cookie solo viaja por conexiones seguras. */
    @org.springframework.beans.factory.annotation.Value("${app.cookie.secure:false}")
    private boolean cookieSegura;

    public record RegistroRequest(String nombre, String correo, String password) {
    }

    public record VerificarRequest(String token) {
    }

    public record CorreoRequest(String correo) {
    }

    public record LoginRequest(String correo, String password) {
    }

    private final AuthService authService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final boolean devMode;

    public AuthController(AuthService authService, JwtService jwtService, EmailService emailService,
                          @Value("${app.auth.dev-mode:false}") boolean devMode) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.devMode = devMode;
    }

    private static final String MENSAJE_ENVIO = "Si el correo es válido, te enviamos un enlace para confirmar tu cuenta. "
            + "Revisa tu bandeja (y la carpeta de spam). El enlace vence en 24 horas.";

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody RegistroRequest r) {
        return ResponseEntity.status(201).body(respuestaEnvio(authService.registrar(r.nombre(), r.correo(), r.password()), r.correo()));
    }

    @PostMapping("/resend-verification")
    public Map<String, Object> reenviar(@RequestBody CorreoRequest r) {
        return respuestaEnvio(authService.reenviar(r.correo()), r.correo());
    }

    @PostMapping("/verify")
    public Map<String, Object> verificar(@RequestBody VerificarRequest r) {
        authService.verificar(r.token());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Cuenta verificada. Ya puedes iniciar sesión.");
        m.put("verificada", true);
        return m;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest r) {
        Usuario u = authService.autenticar(r.correo(), r.password());
        String token = jwtService.generar(u);
        ResponseCookie cookie = ResponseCookie.from(JwtAuthFilter.COOKIE, token)
                .httpOnly(true).secure(cookieSegura).sameSite("Strict").path("/").maxAge(jwtService.getDuracion()).build();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("tipo", "Bearer");
        m.put("id", u.getIdUsuario());
        m.put("nombre", u.getNombre());
        m.put("correo", u.getCorreo());
        m.put("rol", u.getRol().name());
        m.put("redireccion", u.getRol().rutaInicio());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(m);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        ResponseCookie borrar = ResponseCookie.from(JwtAuthFilter.COOKIE, "")
                .httpOnly(true).secure(cookieSegura).sameSite("Strict").path("/").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, borrar.toString())
                .body(Map.of("mensaje", "Sesión cerrada correctamente."));
    }

    @GetMapping("/me")
    public Map<String, Object> yo() {
        var u = Seguridad.actual().orElseThrow(() -> new AuthException(401, "No has iniciado sesión."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("nombre", u.nombre());
        m.put("correo", u.correo());
        m.put("rol", u.rol().name());
        m.put("redireccion", u.rol().rutaInicio());
        return m;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> error(AuthException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage());
        if (e.isRequiereVerificacion()) {
            m.put("requiereVerificacion", true);
        }
        return ResponseEntity.status(e.getStatus()).body(m);
    }

    /** Respuesta idéntica exista o no la cuenta (no se revela si un correo está registrado). El correo se envía en segundo plano. */
    private Map<String, Object> respuestaEnvio(Optional<AuthService.Emitido> emitido, String correo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", MENSAJE_ENVIO);
        m.put("correo", correo == null ? "" : correo.trim().toLowerCase());
        emitido.ifPresent(e -> {
            emailService.enviarVerificacion(e.usuario().getNombre(), e.usuario().getCorreo(), e.token());   // @Async
            if (devMode) {
                // SOLO desarrollo (app.auth.dev-mode=true): facilita probar sin abrir el correo
                m.put("enlaceVerificacion", emailService.enlaceVerificacion(e.token()));
            }
        });
        return m;
    }
}
