package com.hoteleria.gestion_hoteles.exception;

/** Se lanza cuando una operación viola una regla de negocio. */
public class ReglaNegocioException extends RuntimeException {

    private final String campo;

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
        this.campo = null;
    }

    /** @param campo campo del formulario al que se asocia el error (puede ser null). */
    public ReglaNegocioException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
