package com.hoteleria.gestion_hoteles.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Formulario de Check-in (Historia: registrar la llegada del huésped). */
public class CheckInForm {

    @NotNull(message = "Selecciona la habitación que se va a asignar.")
    private Long idHabitacion;

    @NotBlank(message = "El nombre del huésped es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
    private String huesped;

    @NotBlank(message = "La identificación del huésped es obligatoria (INE, pasaporte, licencia).")
    @Size(max = 40, message = "La identificación no puede superar 40 caracteres.")
    private String identificacion;

    @NotNull(message = "Indica cuántas personas se hospedan.")
    @Min(value = 1, message = "Debe hospedarse al menos 1 persona.")
    @Max(value = 20, message = "El número de huéspedes no puede ser mayor a 20.")
    private Integer numHuespedes = 1;

    @NotNull(message = "La fecha de salida prevista es obligatoria.")
    @Future(message = "La fecha de salida debe ser posterior a hoy.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaSalidaPrevista;

    public Long getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(Long idHabitacion) { this.idHabitacion = idHabitacion; }

    public String getHuesped() { return huesped; }
    public void setHuesped(String huesped) { this.huesped = huesped; }

    public String getIdentificacion() { return identificacion; }
    public void setIdentificacion(String identificacion) { this.identificacion = identificacion; }

    public Integer getNumHuespedes() { return numHuespedes; }
    public void setNumHuespedes(Integer numHuespedes) { this.numHuespedes = numHuespedes; }

    public LocalDate getFechaSalidaPrevista() { return fechaSalidaPrevista; }
    public void setFechaSalidaPrevista(LocalDate fechaSalidaPrevista) { this.fechaSalidaPrevista = fechaSalidaPrevista; }
}
