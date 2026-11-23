package com.hoteleria.gestion_hoteles.seguridad;

import com.hoteleria.gestion_hoteles.entity.Rol;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/** Genera y valida tokens JWT (HS256). El token lleva id, correo, nombre y rol del usuario. */
@Service
public class JwtService {

    private final byte[] secreto;
    private final Duration duracion;

    public JwtService(@Value("${app.jwt.secret}") String secreto, @Value("${app.jwt.horas:8}") long horas) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret debe tener al menos 32 caracteres (variable JWT_SECRET).");
        }
        this.secreto = bytes;
        this.duracion = Duration.ofHours(horas);
    }

    public Duration getDuracion() {
        return duracion;
    }

    public String generar(Usuario u) {
        try {
            Date ahora = new Date();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(u.getIdUsuario()))
                    .claim("correo", u.getCorreo())
                    .claim("nombre", u.getNombre())
                    .claim("rol", u.getRol().name())
                    .issueTime(ahora)
                    .expirationTime(new Date(ahora.getTime() + duracion.toMillis()))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secreto));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token JWT.", e);
        }
    }

    /** Devuelve el usuario del token, o vacío si la firma es inválida, está vencido o malformado. */
    public Optional<UsuarioActual> validar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secreto))) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (c.getExpirationTime() == null || c.getExpirationTime().before(new Date())) {
                return Optional.empty();
            }
            return Optional.of(new UsuarioActual(Long.valueOf(c.getSubject()), c.getStringClaim("correo"),
                    c.getStringClaim("nombre"), Rol.valueOf(c.getStringClaim("rol"))));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
