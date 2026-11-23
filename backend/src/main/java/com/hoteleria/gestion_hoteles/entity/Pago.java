package com.hoteleria.gestion_hoteles.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago SIMULADO con tarjeta. Por seguridad solo se guardan la marca y los últimos 4 dígitos:
 * el número completo y el CVV NUNCA se almacenan ni se registran en logs.
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reservacion", nullable = false)
    private Reservacion reservacion;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo", nullable = false, length = 20)
    private String metodo = "TARJETA";

    @Column(name = "marca", length = 20)
    private String marca;

    @Column(name = "ultimos4", nullable = false, length = 4)
    private String ultimos4;

    @Column(name = "titular", nullable = false, length = 120)
    private String titular;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoPago estado;

    @Column(name = "referencia", nullable = false, unique = true, length = 30)
    private String referencia;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public Pago() { }

    public Long getIdPago() { return idPago; }
    public Reservacion getReservacion() { return reservacion; }
    public void setReservacion(Reservacion reservacion) { this.reservacion = reservacion; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getUltimos4() { return ultimos4; }
    public void setUltimos4(String ultimos4) { this.ultimos4 = ultimos4; }
    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
