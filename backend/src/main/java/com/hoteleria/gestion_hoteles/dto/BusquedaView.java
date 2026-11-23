package com.hoteleria.gestion_hoteles.dto;

import java.math.BigDecimal;

/** Proyección de la búsqueda avanzada de disponibilidad (Historia 1). */
public interface BusquedaView {
    Long getIdhabitacion();
    String getHotel();
    String getCiudad();
    String getHabitacion();
    Integer getPiso();
    String getTipo();
    String getDescripcion();
    Integer getCapacidad();
    BigDecimal getPrecio();
}
