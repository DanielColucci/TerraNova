package com.hoteleria.gestion_hoteles.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Datos del panel y de los informes del GERENTE para un periodo y (opcionalmente) un hotel. */
public class GerenteInforme {

    static int pct(long parte, long todo) {
        if (todo <= 0) { return 0; }
        return (int) Math.min(100, Math.round(parte * 100.0 / todo));
    }

    static int pct(BigDecimal parte, BigDecimal todo) {
        if (todo == null || todo.signum() <= 0 || parte == null) { return 0; }
        return parte.multiply(BigDecimal.valueOf(100)).divide(todo, 0, RoundingMode.HALF_UP).intValue();
    }

    /** Fila por hotel. */
    public static class FilaHotel {
        public final long id;
        public final String hotel;
        public final String ciudad;
        public final long habitaciones;
        public final long ocupadasAhora;
        public final long nochesVendidas;
        public final long nochesDisponibles;
        public final BigDecimal ingresos;
        public final long pagos;
        public final int pctIngreso;

        public FilaHotel(long id, String hotel, String ciudad, long habitaciones, long ocupadasAhora,
                         long nochesVendidas, long nochesDisponibles, BigDecimal ingresos, long pagos,
                         BigDecimal ingresosTotales) {
            this.id = id; this.hotel = hotel; this.ciudad = ciudad; this.habitaciones = habitaciones;
            this.ocupadasAhora = ocupadasAhora; this.nochesVendidas = nochesVendidas;
            this.nochesDisponibles = nochesDisponibles; this.ingresos = ingresos; this.pagos = pagos;
            this.pctIngreso = pct(ingresos, ingresosTotales);
        }
        public int getPctOcupacionActual() { return pct(ocupadasAhora, habitaciones); }
        public int getPctOcupacionHistorica() { return pct(nochesVendidas, nochesDisponibles); }
    }

    /** Fila por tipo de habitación (ya ordenada de más a menos rentable). */
    public static class FilaTipo {
        public final String tipo;
        public final BigDecimal ingresos;
        public final long pagos;
        public final long reservaciones;
        public final BigDecimal promNoches;
        public final int pctIngreso;

        public FilaTipo(String tipo, BigDecimal ingresos, long pagos, long reservaciones,
                        BigDecimal promNoches, BigDecimal ingresosTotales) {
            this.tipo = tipo; this.ingresos = ingresos; this.pagos = pagos;
            this.reservaciones = reservaciones; this.promNoches = promNoches;
            this.pctIngreso = pct(ingresos, ingresosTotales);
        }
        public BigDecimal getTicketPromedio() {
            return pagos <= 0 ? BigDecimal.ZERO : ingresos.divide(BigDecimal.valueOf(pagos), 2, RoundingMode.HALF_UP);
        }
    }

    /** Barra del ingreso por día o por mes. */
    public static class FilaPeriodo {
        public final String etiqueta;
        public final BigDecimal ingresos;
        public final int pctBarra;

        public FilaPeriodo(String etiqueta, BigDecimal ingresos, BigDecimal maximo) {
            this.etiqueta = etiqueta; this.ingresos = ingresos; this.pctBarra = pct(ingresos, maximo);
        }
    }

    /** Reservaciones registradas en el periodo, por estado. */
    public static class Estados {
        public long total, confirmadas, pendientes, pendientePago, vencidas, canceladas, canceladasSinPago;
        public BigDecimal montoConfirmado = BigDecimal.ZERO;
        public BigDecimal montoCancelado = BigDecimal.ZERO;

        public int getPctConfirmadas() { return pct(confirmadas, total); }
        public int getPctPendientes() { return pct(pendientes + pendientePago, total); }
        public int getPctVencidas() { return pct(vencidas, total); }
        public int getPctCanceladas() { return pct(canceladas, total); }
        /** Porcentaje de reservaciones que terminó cancelada o con la retención de pago vencida. */
        public int getPctPerdidas() { return pct(canceladas + vencidas, total); }
    }

    public LocalDate desde;
    public LocalDate hasta;
    public long dias;
    public Long idHotel;
    public String hotelNombre = "Todas las sedes";
    public String agrupacion = "día";

    public BigDecimal ingresosTotal = BigDecimal.ZERO;
    public long pagosAprobados;
    public long nochesVendidas;
    public long nochesDisponibles;
    public long habitacionesTotal;
    public long ocupadasAhora;
    public BigDecimal promedioEstancia = BigDecimal.ZERO;
    public Estados estados = new Estados();

    public List<FilaHotel> hoteles = new ArrayList<>();
    public List<FilaTipo> tipos = new ArrayList<>();
    public List<FilaPeriodo> periodo = new ArrayList<>();

    public BigDecimal getTicketPromedio() {
        return pagosAprobados <= 0 ? BigDecimal.ZERO
                : ingresosTotal.divide(BigDecimal.valueOf(pagosAprobados), 2, RoundingMode.HALF_UP);
    }
    public int getPctOcupacionActual() { return pct(ocupadasAhora, habitacionesTotal); }
    public int getPctOcupacionHistorica() { return pct(nochesVendidas, nochesDisponibles); }
    /** Ingreso promedio por noche vendida (ADR) del periodo, a partir de los pagos aprobados. */
    public BigDecimal getIngresoPorNoche() {
        return nochesVendidas <= 0 ? BigDecimal.ZERO
                : ingresosTotal.divide(BigDecimal.valueOf(nochesVendidas), 2, RoundingMode.HALF_UP);
    }
    public FilaTipo getTipoTop() { return tipos.isEmpty() ? null : tipos.get(0); }

    /** Fila genérica de "visión": una etiqueta con cantidad, monto y barras proporcionales. */
    public static class FilaClave {
        public final String etiqueta;
        public final long total;
        public final BigDecimal monto;
        public final int pctMonto;
        public final int pctTotal;
        public final int pctParte;

        public FilaClave(String etiqueta, long total, BigDecimal monto, long maxTotal, BigDecimal maxMonto, long sumaTotal) {
            this.etiqueta = etiqueta; this.total = total; this.monto = monto;
            this.pctMonto = pct(monto, maxMonto);
            this.pctTotal = pct(total, maxTotal);
            this.pctParte = pct(total, sumaTotal);
        }
    }

    // ---- Visión ampliada del gerente
    public LocalDate desdePrevio;
    public LocalDate hastaPrevio;
    public BigDecimal ingresosPrevio = BigDecimal.ZERO;
    public List<FilaClave> anios = new ArrayList<>();          // evolución anual (todo el historial, respeta la sede)
    public List<FilaClave> meses = new ArrayList<>();          // temporada: ingresos por mes del año dentro del periodo
    public List<FilaClave> procedencia = new ArrayList<>();    // de dónde vienen los huéspedes
    public List<FilaClave> origen = new ArrayList<>();         // reservaciones WEB vs IA
    public List<FilaClave> metodos = new ArrayList<>();        // cómo pagan

    /** Variación % de ingresos contra el periodo inmediato anterior de igual duración; null si no hay base. */
    public Integer getVariacionIngresos() {
        if (ingresosPrevio == null || ingresosPrevio.signum() <= 0) { return null; }
        return ingresosTotal.subtract(ingresosPrevio).multiply(BigDecimal.valueOf(100))
                .divide(ingresosPrevio, 0, RoundingMode.HALF_UP).intValue();
    }
    public FilaClave getMesTop() { return meses.stream().max(java.util.Comparator.comparing(f -> f.monto)).orElse(null); }
    public FilaClave getAnioTop() { return anios.stream().max(java.util.Comparator.comparing(f -> f.monto)).orElse(null); }
}
