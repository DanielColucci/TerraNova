package com.hoteleria.gestion_hoteles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Estancia de un huésped en una habitación (check-in / check-out).
 * Una estancia está ACTIVA mientras no tenga fecha de salida real.
 * El precio por noche se guarda como "foto" del precio base vigente al hacer el check-in,
 * de modo que cambiar la tarifa del tipo no altera estancias ya registradas.
 *
 * Check-in sobre reservación: cuando la estancia nace de una reservación existente, {@link #reservacion} la enlaza
 * (una reservación solo puede tener UNA estancia), {@link #fechaEntrada} guarda la fecha/hora REAL de llegada y
 * {@link #idUsuarioCheckin} el usuario de personal que lo procesó. Ambos son null en estancias antiguas (walk-in).
 */
@Entity
@Table(name = "estancia")
public class Estancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estancia")
    private Long idEstancia;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_habitacion", nullable = false,
            foreignKey = @ForeignKey(name = "fk_estancia_habitacion"))
    private Habitacion habitacion;

    /** Reservación de origen (check-in validado). Null en estancias sin reservación previa. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reservacion", unique = true,
            foreignKey = @ForeignKey(name = "fk_estancia_reservacion"))
    private Reservacion reservacion;

    /** Usuario (ADMINISTRADOR/GERENTE/RECEPCIONISTA) que procesó el check-in. */
    @Column(name = "id_usuario_checkin")
    private Long idUsuarioCheckin;

    @Column(name = "huesped", nullable = false, length = 120)
    private String huesped;

    @Column(name = "identificacion", nullable = false, length = 40)
    private String identificacion;

    @Column(name = "num_huespedes", nullable = false)
    private Integer numHuespedes;

    @Column(name = "fecha_entrada", nullable = false)
    private LocalDateTime fechaEntrada;

    @Column(name = "fecha_salida_prevista", nullable = false)
    private LocalDate fechaSalidaPrevista;

    @Column(name = "fecha_salida_real")
    private LocalDateTime fechaSalidaReal;

    @Column(name = "precio_noche", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioNoche;

    @Column(name = "noches")
    private Integer noches;

    @Column(name = "cargos_extra", precision = 10, scale = 2)
    private BigDecimal cargosExtra;

    @Column(name = "total", precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    public Estancia() {
    }

    /** true mientras el huésped sigue hospedado (aún no hay check-out). */
    public boolean isActiva() {
        return fechaSalidaReal == null;
    }

    /** true si la estancia sigue activa y ya pasó la fecha de salida prevista. */
    public boolean isVencida() {
        return isActiva() && fechaSalidaPrevista != null && fechaSalidaPrevista.isBefore(LocalDate.now());
    }

    public Long getIdEstancia() { return idEstancia; }
    public void setIdEstancia(Long idEstancia) { this.idEstancia = idEstancia; }

    public Habitacion getHabitacion() { return habitacion; }
    public void setHabitacion(Habitacion habitacion) { this.habitacion = habitacion; }

    public Reservacion getReservacion() { return reservacion; }
    public void setReservacion(Reservacion reservacion) { this.reservacion = reservacion; }

    public Long getIdUsuarioCheckin() { return idUsuarioCheckin; }
    public void setIdUsuarioCheckin(Long idUsuarioCheckin) { this.idUsuarioCheckin = idUsuarioCheckin; }

    public String getHuesped() { return huesped; }
    public void setHuesped(String huesped) { this.huesped = huesped; }

    public String getIdentificacion() { return identificacion; }
    public void setIdentificacion(String identificacion) { this.identificacion = identificacion; }

    public Integer getNumHuespedes() { return numHuespedes; }
    public void setNumHuespedes(Integer numHuespedes) { this.numHuespedes = numHuespedes; }

    public LocalDateTime getFechaEntrada() { return fechaEntrada; }
    public void setFechaEntrada(LocalDateTime fechaEntrada) { this.fechaEntrada = fechaEntrada; }

    public LocalDate getFechaSalidaPrevista() { return fechaSalidaPrevista; }
    public void setFechaSalidaPrevista(LocalDate fechaSalidaPrevista) { this.fechaSalidaPrevista = fechaSalidaPrevista; }

    public LocalDateTime getFechaSalidaReal() { return fechaSalidaReal; }
    public void setFechaSalidaReal(LocalDateTime fechaSalidaReal) { this.fechaSalidaReal = fechaSalidaReal; }

    public BigDecimal getPrecioNoche() { return precioNoche; }
    public void setPrecioNoche(BigDecimal precioNoche) { this.precioNoche = precioNoche; }

    public Integer getNoches() { return noches; }
    public void setNoches(Integer noches) { this.noches = noches; }

    public BigDecimal getCargosExtra() { return cargosExtra; }
    public void setCargosExtra(BigDecimal cargosExtra) { this.cargosExtra = cargosExtra; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
}
