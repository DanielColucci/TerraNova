package com.hoteleria.gestion_hoteles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * HOTEL 1:N HABITACION N:1 TIPO_HABITACION.
 * El número puede repetirse entre hoteles, pero no dentro del mismo hotel
 * (restricción única compuesta id_hotel + numero).
 */
@Entity
@Table(name = "habitacion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_habitacion_hotel_numero",
                columnNames = {"id_hotel", "numero"}))
public class Habitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_habitacion")
    private Long idHabitacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_hotel", nullable = false,
            foreignKey = @ForeignKey(name = "fk_habitacion_hotel"))
    private Hotel hotel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_tipo", nullable = false,
            foreignKey = @ForeignKey(name = "fk_habitacion_tipo"))
    private TipoHabitacion tipo;

    @Column(name = "numero", nullable = false, length = 10)
    private String numero;

    @Column(name = "piso", nullable = false)
    private Integer piso;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoHabitacion estado = EstadoHabitacion.Disponible;

    public Habitacion() {
    }

    public Long getIdHabitacion() { return idHabitacion; }
    public void setIdHabitacion(Long idHabitacion) { this.idHabitacion = idHabitacion; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public TipoHabitacion getTipo() { return tipo; }
    public void setTipo(TipoHabitacion tipo) { this.tipo = tipo; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public Integer getPiso() { return piso; }
    public void setPiso(Integer piso) { this.piso = piso; }

    public EstadoHabitacion getEstado() { return estado; }
    public void setEstado(EstadoHabitacion estado) { this.estado = estado; }
}
