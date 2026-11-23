package com.hoteleria.gestion_hoteles.dto;

/** Proyección del dashboard ejecutivo: métricas por hotel (Historia 5). */
public interface EjecutivoHotelView {
    String getHotel();
    String getCiudad();
    Number getActivo();
    Number getTotal();
    Number getDisponibles();
    Number getOcupadas();
    Number getMantenimiento();
    Number getIngreso();
    Number getCosto();
    Number getPotencial();
}
