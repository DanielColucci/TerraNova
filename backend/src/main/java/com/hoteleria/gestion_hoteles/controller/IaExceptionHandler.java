package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.ia.service.AsistenteNoDisponibleException;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores claros (texto plano) para los endpoints del asistente de IA. */
@RestControllerAdvice(assignableTypes = {ChatController.class, HotelAiController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IaExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> solicitudInvalida(IllegalArgumentException ex) {
        return texto(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AsistenteNoDisponibleException.class)
    public ResponseEntity<String> asistenteNoDisponible(AsistenteNoDisponibleException ex) {
        return texto(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> baseDeDatos(DataAccessException ex) {
        return texto(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo acceder a la base de datos. Verifica que MySQL esté en ejecución e inténtalo de nuevo.");
    }

    private ResponseEntity<String> texto(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado)
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(mensaje);
    }
}
