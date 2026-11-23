package com.hoteleria.gestion_hoteles.dto;

/** Proyección del dashboard ejecutivo: ocupación por tipo de habitación (hoteles activos). */
public interface EjecutivoTipoView {
    String getTipo();
    Number getCapacidad();
    Number getPrecio();
    Number getTotal();
    Number getOcupadas();
    Number getMantenimiento();
}
