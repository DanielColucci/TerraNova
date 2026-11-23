package com.hoteleria.gestion_hoteles.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Formulario de reservación manual (pantalla /reservaciones). Las reglas de negocio las valida ReservacionService. */
public class ReservacionForm {

    @NotNull(message = "Selecciona la habitación que se va a reservar.")
    private Long idHabitacion;

    @NotBlank(message = "El nombre del huésped es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
    private String nombreHuesped;

    @NotBlank(message = "El teléfono es obligatorio.")
    @Size(max = 20, message = "El teléfono no puede superar 20 caracteres.")
    private String telefono;

    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    private String correo;

    @Size(max = 80, message = "La ciudad no puede superar 80 caracteres.")
    private String ciudadProcedencia;

    @NotNull(message = "La fecha de entrada es obligatoria.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaEntrada;

    @NotNull(message = "La fecha de salida es obligatoria.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaSalida;

    @NotNull(message = "Indica cuántos adultos.")
    @Min(value = 1, message = "Debe haber al menos 1 adulto.")
    @Max(value = 20, message = "Máximo 20 adultos.")
    private Integer adultos = 2;

    @NotNull(message = "Indica cuántos niños (0 si no hay).")
    @Min(value = 0, message = "El número de niños no puede ser negativo.")
    @Max(value = 20, message = "Máximo 20 niños.")
    private Integer ninos = 0;

    public Long getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(Long idHabitacion) { this.idHabitacion = idHabitacion; }
    public String getNombreHuesped() { return nombreHuesped; }
    public void setNombreHuesped(String nombreHuesped) { this.nombreHuesped = nombreHuesped; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getCiudadProcedencia() { return ciudadProcedencia; }
    public void setCiudadProcedencia(String ciudadProcedencia) { this.ciudadProcedencia = ciudadProcedencia; }
    public LocalDate getFechaEntrada() { return fechaEntrada; }
    public void setFechaEntrada(LocalDate fechaEntrada) { this.fechaEntrada = fechaEntrada; }
    public LocalDate getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(LocalDate fechaSalida) { this.fechaSalida = fechaSalida; }
    public Integer getAdultos() { return adultos; }
    public void setAdultos(Integer adultos) { this.adultos = adultos; }
    public Integer getNinos() { return ninos; }
    public void setNinos(Integer ninos) { this.ninos = ninos; }
}
