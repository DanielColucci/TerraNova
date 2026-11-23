package com.hoteleria.gestion_hoteles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reservación de una habitación para un rango de fechas [fechaEntrada, fechaSalida).
 * El precio por noche se guarda al reservar (no cambia si luego se modifica la tarifa del tipo).
 * Los datos del huésped van en la misma fila (igual que en NovaTerra); no hay tabla de clientes.
 */
@Entity
@Table(name = "reservacion")
public class Reservacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reservacion")
    private Long idReservacion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_habitacion", nullable = false)
    private Habitacion habitacion;

    @Column(name = "nombre_huesped", nullable = false, length = 120)
    private String nombreHuesped;

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @Column(name = "correo", length = 120)
    private String correo;

    @Column(name = "ciudad_procedencia", length = 80)
    private String ciudadProcedencia;

    @Column(name = "fecha_entrada", nullable = false)
    private LocalDate fechaEntrada;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDate fechaSalida;

    @Column(name = "adultos", nullable = false)
    private Integer adultos = 1;

    @Column(name = "ninos", nullable = false)
    private Integer ninos = 0;

    @Column(name = "noches", nullable = false)
    private Integer noches;

    @Column(name = "precio_noche", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioNoche;

    @Column(name = "costo_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReservacion estado = EstadoReservacion.Confirmada;

    /** WEB o IA: quién la registró. */
    /** Usuario (cuenta) dueño de la reservación; null si la registró recepción/administración. */
    @Column(name = "id_usuario")
    private Long idUsuario;

    /**
     * Fecha/hora real en que el huésped llegó (check-in procesado). Null mientras no haya llegado.
     * Marca de consulta rápida: el detalle (quién, habitación, folio) vive en {@link Estancia}.
     */
    @Column(name = "fecha_checkin")
    private LocalDateTime fechaCheckin;

    @Column(name = "origen", nullable = false, length = 10)
    private String origen = "WEB";

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public Reservacion() {
    }

    /** true si el huésped ya llegó (check-in registrado). */
    public boolean isCheckinRealizado() {
        return fechaCheckin != null;
    }

    public int getPersonas() {
        return adultos + ninos;
    }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public Long getIdReservacion() { return idReservacion; }
    public void setIdReservacion(Long idReservacion) { this.idReservacion = idReservacion; }
    public Habitacion getHabitacion() { return habitacion; }
    public void setHabitacion(Habitacion habitacion) { this.habitacion = habitacion; }
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
    public Integer getNoches() { return noches; }
    public void setNoches(Integer noches) { this.noches = noches; }
    public BigDecimal getPrecioNoche() { return precioNoche; }
    public void setPrecioNoche(BigDecimal precioNoche) { this.precioNoche = precioNoche; }
    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal costoTotal) { this.costoTotal = costoTotal; }
    public EstadoReservacion getEstado() { return estado; }
    public void setEstado(EstadoReservacion estado) { this.estado = estado; }
    public LocalDateTime getFechaCheckin() { return fechaCheckin; }
    public void setFechaCheckin(LocalDateTime fechaCheckin) { this.fechaCheckin = fechaCheckin; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
