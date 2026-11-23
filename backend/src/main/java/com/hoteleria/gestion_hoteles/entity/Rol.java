package com.hoteleria.gestion_hoteles.entity;

/**
 * Rol de la cuenta. El registro público siempre crea USUARIO (huésped).
 * ADMINISTRADOR, GERENTE y RECEPCIONISTA son personal del hotel y los crea un administrador.
 */
public enum Rol {
    ADMINISTRADOR,
    GERENTE,
    RECEPCIONISTA,
    USUARIO;

    /** true para cuentas de personal (todo lo que no es huésped). */
    public boolean esPersonal() {
        return this != USUARIO;
    }

    /** Quién puede procesar check-in / check-out en recepción. */
    public boolean puedeOperarRecepcion() {
        return this == ADMINISTRADOR || this == GERENTE || this == RECEPCIONISTA;
    }

    /** Ruta a la que se envía al usuario tras iniciar sesión. */
    public String rutaInicio() {
        return switch (this) {
            case ADMINISTRADOR -> "/admin/dashboard";
            case GERENTE -> "/gerente/dashboard";
            case RECEPCIONISTA -> "/recepcion/panel";
            case USUARIO -> "/inicio";
        };
    }
}
