package com.hoteleria.gestion_hoteles.dto;

import java.math.BigDecimal;

/** Fila de resultado de la búsqueda avanzada, con el total estimado de la estancia (si se indicaron fechas). */
public class ResultadoBusqueda {

    private final Long idHabitacion;
    private final String hotel;
    private final String ciudad;
    private final String habitacion;
    private final Integer piso;
    private final String tipo;
    private final String descripcion;
    private final Integer capacidad;
    private final BigDecimal precio;
    private final BigDecimal totalEstimado;

    public ResultadoBusqueda(BusquedaView v, long noches) {
        this.idHabitacion = v.getIdhabitacion();
        this.hotel = v.getHotel();
        this.ciudad = v.getCiudad();
        this.habitacion = v.getHabitacion();
        this.piso = v.getPiso();
        this.tipo = v.getTipo();
        this.descripcion = v.getDescripcion();
        this.capacidad = v.getCapacidad();
        this.precio = v.getPrecio();
        this.totalEstimado = noches > 0 ? v.getPrecio().multiply(BigDecimal.valueOf(noches)) : null;
    }

    public Long getIdHabitacion() { return idHabitacion; }
    public String getHotel() { return hotel; }
    public String getCiudad() { return ciudad; }
    public String getHabitacion() { return habitacion; }
    public Integer getPiso() { return piso; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public Integer getCapacidad() { return capacidad; }
    public BigDecimal getPrecio() { return precio; }
    /** null cuando no se indicaron fechas. */
    public BigDecimal getTotalEstimado() { return totalEstimado; }
}
