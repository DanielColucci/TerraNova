package com.hoteleria.gestion_hoteles.ia.service;

import java.util.List;

/**
 * Respuesta del asistente.
 * herramientasUsadas permite comprobar que la IA realmente consultó la base de datos.
 * requiereConfirmacion = true cuando hay una acción de reservación esperando el «sí» del usuario.
 */
public record RespuestaIA(String modelo, String respuesta, List<String> herramientasUsadas, long tiempoMs,
                          boolean requiereConfirmacion) {

    public RespuestaIA(String modelo, String respuesta, List<String> herramientasUsadas, long tiempoMs) {
        this(modelo, respuesta, herramientasUsadas, tiempoMs, false);
    }
}
