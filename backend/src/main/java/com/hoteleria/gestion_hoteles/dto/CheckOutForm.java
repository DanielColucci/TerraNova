package com.hoteleria.gestion_hoteles.dto;

import com.hoteleria.gestion_hoteles.entity.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Formulario de Check-out: cargos adicionales (consumos) y forma de pago del saldo. */
public class CheckOutForm {

    @NotNull(message = "Indica los cargos adicionales (usa 0 si no hay).")
    @DecimalMin(value = "0.0", message = "Los cargos adicionales no pueden ser negativos.")
    @Digits(integer = 8, fraction = 2, message = "Los cargos admiten hasta 8 enteros y 2 decimales.")
    private BigDecimal cargosExtra = BigDecimal.ZERO;

    @NotNull(message = "Selecciona la forma de pago.")
    private MetodoPago metodoPago = MetodoPago.Efectivo;

    /** Estado en que queda la habitación al salir el huésped: Mantenimiento (limpieza) o Disponible. */
    private String estadoHabitacion = "Mantenimiento";

    public String getEstadoHabitacion() { return estadoHabitacion; }
    public void setEstadoHabitacion(String estadoHabitacion) { this.estadoHabitacion = estadoHabitacion; }

    public BigDecimal getCargosExtra() { return cargosExtra; }
    public void setCargosExtra(BigDecimal cargosExtra) { this.cargosExtra = cargosExtra; }

    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
}
