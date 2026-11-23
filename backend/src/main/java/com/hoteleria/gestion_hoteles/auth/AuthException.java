package com.hoteleria.gestion_hoteles.auth;

/** Error de autenticación/registro con el código HTTP que debe devolver la API (400, 401, 403, 409). */
public class AuthException extends RuntimeException {

    private final int status;
    private final boolean requiereVerificacion;

    public AuthException(int status, String mensaje) {
        this(status, mensaje, false);
    }

    public AuthException(int status, String mensaje, boolean requiereVerificacion) {
        super(mensaje);
        this.status = status;
        this.requiereVerificacion = requiereVerificacion;
    }

    public int getStatus() { return status; }
    public boolean isRequiereVerificacion() { return requiereVerificacion; }
}
