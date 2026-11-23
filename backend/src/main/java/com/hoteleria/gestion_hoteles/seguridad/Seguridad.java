package com.hoteleria.gestion_hoteles.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Acceso cómodo al usuario autenticado de la petición actual (servicios, herramientas de IA, controladores). */
public final class Seguridad {

    private Seguridad() {
    }

    public static Optional<UsuarioActual> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioActual u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }
}
