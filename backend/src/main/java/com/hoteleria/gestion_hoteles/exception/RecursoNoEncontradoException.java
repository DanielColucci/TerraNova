package com.hoteleria.gestion_hoteles.exception;

/** Se lanza cuando un registro solicitado no existe. */
public class RecursoNoEncontradoException extends RuntimeException {

    private final String redireccion;

    public RecursoNoEncontradoException(String mensaje, String redireccion) {
        super(mensaje);
        this.redireccion = redireccion;
    }

    /** Ruta a la que se regresa al usuario (por ejemplo "/hoteles"). */
    public String getRedireccion() {
        return redireccion;
    }
}
