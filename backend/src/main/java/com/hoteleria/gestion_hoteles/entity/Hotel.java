package com.hoteleria.gestion_hoteles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotel")
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_hotel")
    private Long idHotel;

    @NotBlank(message = "El nombre del hotel es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @NotBlank(message = "La dirección es obligatoria.")
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres.")
    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @NotBlank(message = "La ciudad es obligatoria.")
    @Size(max = 80, message = "La ciudad no puede superar 80 caracteres.")
    @Column(name = "ciudad", nullable = false, length = 80)
    private String ciudad;

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = "^$|^\\+?[0-9(][0-9\\s\\-()]{6,19}$",
            message = "Teléfono inválido: usa solo números, espacios, guiones o paréntesis (de 7 a 20 caracteres).")
    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
    @Pattern(regexp = "^$|^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$",
            message = "El correo electrónico tiene un formato incorrecto (ejemplo: hotel@terranova.com).")
    @Column(name = "correo", nullable = false, length = 120)
    private String correo;

    @NotNull(message = "Indica si el hotel está activo.")
    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;

    @OneToMany(mappedBy = "hotel")
    private List<Habitacion> habitaciones = new ArrayList<>();

    public Hotel() {
    }

    public Long getIdHotel() { return idHotel; }
    public void setIdHotel(Long idHotel) { this.idHotel = idHotel; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public List<Habitacion> getHabitaciones() { return habitaciones; }
    public void setHabitaciones(List<Habitacion> habitaciones) { this.habitaciones = habitaciones; }

    /** Cantidad de habitaciones asociadas (para tablas y mensajes). */
    public int getTotalHabitaciones() {
        return habitaciones == null ? 0 : habitaciones.size();
    }
}
