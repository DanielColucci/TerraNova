package com.hoteleria.gestion_hoteles.seguridad;

import com.hoteleria.gestion_hoteles.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Seguridad del Backend API REST (stateless + JWT Bearer + CORS configurable por entorno).
 * Los permisos por rol se hacen cumplir en el servidor para cada módulo bajo /api/**.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5500}")
    private String allowedOrigins;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Evita que Spring Boot cree el usuario «user» con contraseña aleatoria: aquí la autenticación es por JWT. */
    @Bean
    UserDetailsService userDetailsService() {
        return correo -> { throw new UsernameNotFoundException(correo); };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        List<String> origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origenes);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "ngrok-skip-browser-warning"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtService jwtService, UsuarioRepository usuarioRepository) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/error/**",
                                "/api/auth/register", "/api/auth/verify",
                                "/api/auth/resend-verification", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        // Huésped (el administrador también puede consultar estas rutas)
                        .requestMatchers("/api/usuario/**", "/api/pago/**").hasAnyRole("ADMINISTRADOR", "USUARIO")
                        // Asistente de IA: cada rol recibe sus propias herramientas (ver AiConfig / HotelAssistantService)
                        .requestMatchers("/api/v1/chat/**", "/api/hotel-ai/**")
                        .hasAnyRole("ADMINISTRADOR", "USUARIO", "GERENTE", "RECEPCIONISTA")
                        // Recepción
                        .requestMatchers("/api/recepcion/**").hasAnyRole("RECEPCIONISTA", "ADMINISTRADOR")
                        // Gerencia
                        .requestMatchers("/api/gerente/**").hasAnyRole("GERENTE", "ADMINISTRADOR")
                        // Exclusivo del administrador
                        .requestMatchers("/api/admin/**", "/api/usuarios/**", "/api/hoteles/**",
                                "/api/tipos/**", "/api/habitaciones/**", "/api/reservaciones/**",
                                "/api/busqueda/**").hasRole("ADMINISTRADOR")
                        .anyRequest().hasRole("ADMINISTRADOR"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        "{\"error\":\"No has iniciado sesión o tu sesión expiró.\"}"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN,
                                        "{\"error\":\"Tu rol no tiene permiso para esta acción.\"}")))
                .addFilterBefore(new JwtAuthFilter(jwtService, usuarioRepository), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String cuerpo) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(cuerpo.getBytes(StandardCharsets.UTF_8));
    }
}
