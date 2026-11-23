package com.hoteleria.gestion_hoteles.seguridad;

import com.hoteleria.gestion_hoteles.entity.Rol;

/** Usuario autenticado (sale del JWT). Es el «principal» de Spring Security. */
public record UsuarioActual(Long id, String correo, String nombre, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMINISTRADOR;
    }

    public boolean esGerente() {
        return rol == Rol.GERENTE;
    }

    public boolean esRecepcionista() {
        return rol == Rol.RECEPCIONISTA;
    }

    /** Cualquier cuenta de personal (no huésped). */
    public boolean esPersonal() {
        return rol != null && rol.esPersonal();
    }

    // Getters estilo JavaBean para usarlos desde Thymeleaf (${usuarioActual.nombre})
    public Long getId() { return id; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
}
