package com.hoteleria.gestion_hoteles.dto;

import java.math.BigDecimal;
import java.util.List;

/** Datos del dashboard ejecutivo (Historia 5): KPIs globales, detalle por hotel y por tipo. */
public class EjecutivoResumen {

    /** Fila por hotel con porcentajes ya calculados. */
    public static class FilaHotel {
        private final String hotel;
        private final String ciudad;
        private final boolean activo;
        private final long total;
        private final long disponibles;
        private final long ocupadas;
        private final long mantenimiento;
        private final BigDecimal ingreso;
        private final BigDecimal costo;

        public FilaHotel(String hotel, String ciudad, boolean activo, long total, long disponibles,
                         long ocupadas, long mantenimiento, BigDecimal ingreso, BigDecimal costo) {
            this.hotel = hotel;
            this.ciudad = ciudad;
            this.activo = activo;
            this.total = total;
            this.disponibles = disponibles;
            this.ocupadas = ocupadas;
            this.mantenimiento = mantenimiento;
            this.ingreso = ingreso;
            this.costo = costo;
        }

        public String getHotel() { return hotel; }
        public String getCiudad() { return ciudad; }
        public boolean isActivo() { return activo; }
        public long getTotal() { return total; }
        public long getDisponibles() { return disponibles; }
        public long getOcupadas() { return ocupadas; }
        public long getMantenimiento() { return mantenimiento; }
        public BigDecimal getIngreso() { return ingreso; }
        public BigDecimal getCosto() { return costo; }

        public int getPctOcupacion() { return pct(ocupadas, total); }
        public int getPctDisponible() { return pct(disponibles, total); }
        public int getPctMantenimiento() { return pct(mantenimiento, total); }
    }

    /** Fila por tipo de habitación (solo hoteles activos). */
    public static class FilaTipo {
        private final String tipo;
        private final int capacidad;
        private final BigDecimal precio;
        private final long total;
        private final long ocupadas;
        private final long mantenimiento;

        public FilaTipo(String tipo, int capacidad, BigDecimal precio, long total, long ocupadas, long mantenimiento) {
            this.tipo = tipo;
            this.capacidad = capacidad;
            this.precio = precio;
            this.total = total;
            this.ocupadas = ocupadas;
            this.mantenimiento = mantenimiento;
        }

        public String getTipo() { return tipo; }
        public int getCapacidad() { return capacidad; }
        public BigDecimal getPrecio() { return precio; }
        public long getTotal() { return total; }
        public long getOcupadas() { return ocupadas; }
        public long getMantenimiento() { return mantenimiento; }
        public int getPctOcupacion() { return pct(ocupadas, total); }
    }

    private final long total;
    private final long disponibles;
    private final long ocupadas;
    private final long mantenimiento;
    private final BigDecimal ingresoDiario;
    private final BigDecimal costoOportunidad;
    private final BigDecimal ingresoPotencial;
    private final BigDecimal ingresosMes;
    private final long estanciasActivas;
    private final List<FilaHotel> hoteles;
    private final List<FilaTipo> tipos;

    public EjecutivoResumen(long total, long disponibles, long ocupadas, long mantenimiento,
                            BigDecimal ingresoDiario, BigDecimal costoOportunidad, BigDecimal ingresoPotencial,
                            BigDecimal ingresosMes, long estanciasActivas,
                            List<FilaHotel> hoteles, List<FilaTipo> tipos) {
        this.total = total;
        this.disponibles = disponibles;
        this.ocupadas = ocupadas;
        this.mantenimiento = mantenimiento;
        this.ingresoDiario = ingresoDiario;
        this.costoOportunidad = costoOportunidad;
        this.ingresoPotencial = ingresoPotencial;
        this.ingresosMes = ingresosMes;
        this.estanciasActivas = estanciasActivas;
        this.hoteles = hoteles;
        this.tipos = tipos;
    }

    static int pct(long parte, long todo) {
        return todo <= 0 ? 0 : (int) Math.round(parte * 100.0 / todo);
    }

    public long getTotal() { return total; }
    public long getDisponibles() { return disponibles; }
    public long getOcupadas() { return ocupadas; }
    public long getMantenimiento() { return mantenimiento; }
    public BigDecimal getIngresoDiario() { return ingresoDiario; }
    public BigDecimal getCostoOportunidad() { return costoOportunidad; }
    public BigDecimal getIngresoPotencial() { return ingresoPotencial; }
    public BigDecimal getIngresosMes() { return ingresosMes; }
    public long getEstanciasActivas() { return estanciasActivas; }
    public List<FilaHotel> getHoteles() { return hoteles; }
    public List<FilaTipo> getTipos() { return tipos; }

    public int getPctOcupacion() { return pct(ocupadas, total); }
    public int getPctDisponible() { return pct(disponibles, total); }
    public int getPctMantenimiento() { return pct(mantenimiento, total); }
    /** Porcentaje del ingreso diario potencial que se deja de percibir por habitaciones en mantenimiento. */
    public int getPctCosto() {
        if (ingresoPotencial == null || ingresoPotencial.signum() <= 0) { return 0; }
        return costoOportunidad.multiply(BigDecimal.valueOf(100))
                .divide(ingresoPotencial, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
