package com.hoteleria.gestion_hoteles.ia.service;

/** El proveedor de IA (Ollama/Gemini) o la base de datos no pudieron atender la pregunta. */
public class AsistenteNoDisponibleException extends RuntimeException {

    public AsistenteNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
