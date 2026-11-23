package com.hoteleria.gestion_hoteles.entity;

/**
 * Estados permitidos de una habitación.
 * Los nombres coinciden exactamente con los valores guardados en la BD
 * ('Disponible', 'Ocupada', 'Mantenimiento').
 */
public enum EstadoHabitacion {
    Disponible,
    Ocupada,
    Mantenimiento;

    /** Sufijo para clases CSS (estado-disponible, estado-ocupada, estado-mantenimiento). */
    public String getCss() {
        return name().toLowerCase();
    }
}
