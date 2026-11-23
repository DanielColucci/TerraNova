package com.hoteleria.gestion_hoteles.dto;

import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos del formulario de habitaciones (hotel y tipo se eligen por id desde listas desplegables). */
public class HabitacionForm {

    private Long idHabitacion;

    @NotNull(message = "Selecciona un hotel.")
    private Long idHotel;

    @NotNull(message = "Selecciona un tipo de habitación.")
    private Long idTipo;

    @NotBlank(message = "El número de habitación es obligatorio.")
    @Size(max = 10, message = "El número no puede superar 10 caracteres.")
    @Pattern(regexp = "^$|^[A-Za-z0-9\\-]+$",
            message = "El número solo puede contener letras, números o guiones (sin espacios).")
    private String numero;

    @NotNull(message = "El piso es obligatorio.")
    @Min(value = 0, message = "Piso inválido: debe ser 0 (planta baja) o mayor.")
    @Max(value = 100, message = "Piso inválido: el máximo permitido es 100.")
    private Integer piso;

    @NotNull(message = "Selecciona un estado.")
    private EstadoHabitacion estado = EstadoHabitacion.Disponible;

    public Long getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(Long idHabitacion) { this.idHabitacion = idHabitacion; }

    public Long getIdHotel() { return idHotel; }
    public void setIdHotel(Long idHotel) { this.idHotel = idHotel; }

    public Long getIdTipo() { return idTipo; }
    public void setIdTipo(Long idTipo) { this.idTipo = idTipo; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public Integer getPiso() { return piso; }
    public void setPiso(Integer piso) { this.piso = piso; }

    public EstadoHabitacion getEstado() { return estado; }
    public void setEstado(EstadoHabitacion estado) { this.estado = estado; }
}
