package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.dto.EjecutivoHotelView;
import com.hoteleria.gestion_hoteles.dto.EjecutivoResumen;
import com.hoteleria.gestion_hoteles.dto.EjecutivoTipoView;
import com.hoteleria.gestion_hoteles.dto.GerenteInforme;
import com.hoteleria.gestion_hoteles.dto.GerenteViews;
import com.hoteleria.gestion_hoteles.dto.ResultadoBusqueda;
import com.hoteleria.gestion_hoteles.dto.ResumenDashboard;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.repository.ConsultaRepository;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final HotelRepository hotelRepository;
    private final TipoHabitacionRepository tipoRepository;
    private final HabitacionRepository habitacionRepository;
    private final EstanciaRepository estanciaRepository;

    /** Minutos que se retiene una reservación Pendiente_Pago (misma propiedad que PagoService). */
    @Value("${app.pago.minutos-retencion:15}")
    private long minutosRetencion = 15;

    public ConsultaService(ConsultaRepository consultaRepository,
                           HotelRepository hotelRepository,
                           TipoHabitacionRepository tipoRepository,
                           HabitacionRepository habitacionRepository,
                           EstanciaRepository estanciaRepository) {
        this.consultaRepository = consultaRepository;
        this.hotelRepository = hotelRepository;
        this.tipoRepository = tipoRepository;
        this.habitacionRepository = habitacionRepository;
        this.estanciaRepository = estanciaRepository;
    }

    /** KPIs reales del panel principal. */
    public ResumenDashboard resumen() {
        return new ResumenDashboard(
                hotelRepository.countByActivoTrue(),
                hotelRepository.count(),
                tipoRepository.count(),
                habitacionRepository.count(),
                habitacionRepository.countByEstadoAndHotel_ActivoTrue(EstadoHabitacion.Disponible),
                habitacionRepository.countByEstado(EstadoHabitacion.Ocupada),
                habitacionRepository.countByEstado(EstadoHabitacion.Mantenimiento));
    }

    /** Ciudades con hoteles activos (lista desplegable de la búsqueda). */
    public List<String> ciudadesActivas() {
        return hotelRepository.findCiudadesActivas();
    }

    /**
     * Historia 1: búsqueda avanzada de disponibilidad.
     * Los filtros en null significan "sin filtro". Si se indican ambas fechas, cada resultado incluye el total estimado.
     */
    public List<ResultadoBusqueda> buscarDisponibilidad(String ciudad, Long idHotel, Long idTipo,
                                                        Integer capacidad, BigDecimal precioMax,
                                                        LocalDate entrada, LocalDate salida) {
        String c = ciudad == null ? "" : ciudad.trim();
        long hotel = idHotel == null ? 0L : idHotel;
        long tipo = idTipo == null ? 0L : idTipo;
        int cap = (capacidad == null || capacidad < 0) ? 0 : capacidad;
        BigDecimal tope = (precioMax == null || precioMax.signum() < 0) ? BigDecimal.valueOf(-1) : precioMax;
        long noches = (entrada != null && salida != null) ? ChronoUnit.DAYS.between(entrada, salida) : 0;

        List<ResultadoBusqueda> resultados = new ArrayList<>();
        consultaRepository.busquedaDisponibilidad(c, hotel, tipo, cap, tope)
                .forEach(v -> resultados.add(new ResultadoBusqueda(v, noches)));
        return resultados;
    }

    /** Historia 5: dashboard ejecutivo. Los KPIs globales consideran solo hoteles activos. */
    public EjecutivoResumen ejecutivo() {
        List<EjecutivoResumen.FilaHotel> hoteles = new ArrayList<>();
        long total = 0, disp = 0, ocup = 0, mant = 0;
        BigDecimal ingreso = BigDecimal.ZERO, costo = BigDecimal.ZERO, potencial = BigDecimal.ZERO;

        for (EjecutivoHotelView v : consultaRepository.ejecutivoPorHotel()) {
            boolean activo = v.getActivo() != null && v.getActivo().intValue() == 1;
            EjecutivoResumen.FilaHotel fila = new EjecutivoResumen.FilaHotel(
                    v.getHotel(), v.getCiudad(), activo,
                    num(v.getTotal()), num(v.getDisponibles()), num(v.getOcupadas()), num(v.getMantenimiento()),
                    dec(v.getIngreso()), dec(v.getCosto()));
            hoteles.add(fila);
            if (activo) {
                total += fila.getTotal();
                disp += fila.getDisponibles();
                ocup += fila.getOcupadas();
                mant += fila.getMantenimiento();
                ingreso = ingreso.add(fila.getIngreso());
                costo = costo.add(fila.getCosto());
                potencial = potencial.add(dec(v.getPotencial()));
            }
        }

        List<EjecutivoResumen.FilaTipo> tipos = new ArrayList<>();
        for (EjecutivoTipoView v : consultaRepository.ejecutivoPorTipo()) {
            tipos.add(new EjecutivoResumen.FilaTipo(v.getTipo(), (int) num(v.getCapacidad()), dec(v.getPrecio()),
                    num(v.getTotal()), num(v.getOcupadas()), num(v.getMantenimiento())));
        }

        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal ingresosMes = estanciaRepository.ingresosDesde(inicioMes);
        return new EjecutivoResumen(total, disp, ocup, mant, ingreso, costo, potencial,
                ingresosMes == null ? BigDecimal.ZERO : ingresosMes,
                estanciaRepository.countByFechaSalidaRealIsNull(), hoteles, tipos);
    }


    /**
     * Fase 4 (GERENTE): ingresos aprobados, ocupación actual e histórica, reservaciones por estado y rentabilidad por tipo.
     * Fechas nulas = mes en curso; si vienen invertidas se intercambian; idHotel null/0 = todas las sedes.
     */
    // Sin transacción global: así, si falla una consulta opcional de la visión ampliada, no se invalida todo el informe.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public GerenteInforme informeGerente(LocalDate desde, LocalDate hasta, Long idHotel) {
        LocalDate hoy = LocalDate.now();
        LocalDate d = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : hoy;
        if (d.isAfter(h)) { LocalDate t = d; d = h; h = t; }
        long hotel = idHotel == null || idHotel < 0 ? 0L : idHotel;
        LocalDateTime desdeTs = d.atStartOfDay();
        LocalDateTime hastaTs = h.plusDays(1).atStartOfDay();
        LocalDate hastaExcl = h.plusDays(1);
        long dias = ChronoUnit.DAYS.between(d, h) + 1;

        GerenteInforme g = new GerenteInforme();
        g.desde = d; g.hasta = h; g.dias = dias; g.idHotel = hotel == 0 ? null : hotel;

        // 1) Por hotel
        List<GerenteViews.HotelView> vh = consultaRepository.gerentePorHotel(d, hastaExcl, desdeTs, hastaTs, hotel);
        for (GerenteViews.HotelView v : vh) { g.ingresosTotal = g.ingresosTotal.add(dec(v.getIngresos())); }
        for (GerenteViews.HotelView v : vh) {
            long hab = num(v.getHabitaciones());
            g.hoteles.add(new GerenteInforme.FilaHotel(num(v.getIdhotel()), v.getHotel(), v.getCiudad(), hab,
                    num(v.getOcupadasahora()), num(v.getNochesvendidas()), hab * dias,
                    dec(v.getIngresos()), num(v.getPagos()), g.ingresosTotal));
            g.pagosAprobados += num(v.getPagos());
            g.nochesVendidas += num(v.getNochesvendidas());
            g.nochesDisponibles += hab * dias;
            g.habitacionesTotal += hab;
            g.ocupadasAhora += num(v.getOcupadasahora());
        }
        if (hotel != 0 && vh.size() == 1) { g.hotelNombre = vh.get(0).getHotel(); }

        // 2) Por tipo (la consulta ya viene ordenada por ingreso)
        BigDecimal sumaNoches = BigDecimal.ZERO;
        long sumaRes = 0;
        for (GerenteViews.TipoView v : consultaRepository.gerentePorTipo(d, hastaExcl, desdeTs, hastaTs, hotel)) {
            long res = num(v.getReservaciones());
            BigDecimal prom = dec(v.getPromnoches()).setScale(1, java.math.RoundingMode.HALF_UP);
            g.tipos.add(new GerenteInforme.FilaTipo(v.getTipo(), dec(v.getIngresos()), num(v.getPagos()),
                    res, prom, g.ingresosTotal));
            sumaNoches = sumaNoches.add(dec(v.getPromnoches()).multiply(BigDecimal.valueOf(res)));
            sumaRes += res;
        }
        g.promedioEstancia = sumaRes == 0 ? BigDecimal.ZERO
                : sumaNoches.divide(BigDecimal.valueOf(sumaRes), 1, java.math.RoundingMode.HALF_UP);

        // 3) Ingresos por periodo: por día (hasta 62 días) o por mes
        boolean porMes = dias > 62;
        g.agrupacion = porMes ? "mes" : "día";
        Map<String, BigDecimal> serie = new LinkedHashMap<>();
        for (GerenteViews.PeriodoView v : consultaRepository.gerenteIngresosPorDia(desdeTs, hastaTs, hotel)) {
            String dia = v.getDia();
            if (dia == null || dia.length() < 10) { continue; }
            String clave = porMes ? dia.substring(0, 7) : dia.substring(8, 10) + "/" + dia.substring(5, 7);
            serie.merge(clave, dec(v.getIngresos()), BigDecimal::add);
        }
        BigDecimal max = serie.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        serie.forEach((k, v) -> g.periodo.add(new GerenteInforme.FilaPeriodo(k, v, max)));

        // 4) Reservaciones por estado
        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosRetencion);
        List<GerenteViews.EstadosView> ev = consultaRepository.gerenteEstados(desdeTs, hastaTs, limite, hotel);
        if (ev != null && !ev.isEmpty() && ev.get(0) != null) {
            GerenteViews.EstadosView e = ev.get(0);
            g.estados.total = num(e.getTotal());
            g.estados.confirmadas = num(e.getConfirmadas());
            g.estados.pendientes = num(e.getPendientes());
            g.estados.pendientePago = num(e.getPendientepago());
            g.estados.vencidas = num(e.getVencidas());
            g.estados.canceladas = num(e.getCanceladas());
            g.estados.canceladasSinPago = num(e.getCanceladassinpago());
            g.estados.montoConfirmado = dec(e.getMontoconfirmado());
            g.estados.montoCancelado = dec(e.getMontocancelado());
        }

        // 5) Visión ampliada: comparativo, evolución anual, temporada, procedencia, origen y método de pago
        try {
            g.hastaPrevio = d.minusDays(1);
            g.desdePrevio = g.hastaPrevio.minusDays(dias - 1);
            BigDecimal prev = consultaRepository.gerenteIngresosEntre(g.desdePrevio.atStartOfDay(), d.atStartOfDay(), hotel);
            g.ingresosPrevio = prev == null ? BigDecimal.ZERO : prev;
            llenarClaves(g.anios, consultaRepository.gerentePorAnio(hotel), false);
            llenarClaves(g.meses, consultaRepository.gerentePorMesDelAnio(desdeTs, hastaTs, hotel), true);
            llenarClaves(g.procedencia, consultaRepository.gerenteProcedencia(d, hastaExcl, hotel), false);
            llenarClaves(g.origen, consultaRepository.gerenteOrigen(desdeTs, hastaTs, hotel), false);
            llenarClaves(g.metodos, consultaRepository.gerenteMetodos(desdeTs, hastaTs, hotel), false);
        } catch (RuntimeException ex) {
            // La visión ampliada es opcional: si una de sus consultas falla, el panel principal sigue funcionando.
            org.slf4j.LoggerFactory.getLogger(ConsultaService.class).warn("Visión ampliada del gerente no disponible: {}", ex.toString(), ex);
        }
        return g;
    }

    /** Sedes para el filtro del gerente. */
    public List<com.hoteleria.gestion_hoteles.entity.Hotel> sedes() {
        return hotelRepository.findAllByOrderByNombreAsc();
    }

    private static final String[] MESES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};

    private static void llenarClaves(List<GerenteInforme.FilaClave> destino, List<GerenteViews.ConteoView> filas, boolean mes) {
        long maxTotal = 0, suma = 0;
        BigDecimal maxMonto = BigDecimal.ZERO;
        for (GerenteViews.ConteoView v : filas) {
            maxTotal = Math.max(maxTotal, num(v.getTotal()));
            suma += num(v.getTotal());
            if (dec(v.getMonto()).compareTo(maxMonto) > 0) { maxMonto = dec(v.getMonto()); }
        }
        for (GerenteViews.ConteoView v : filas) {
            String et = (v.getClave() == null || v.getClave().isBlank()) ? "Sin dato" : v.getClave();
            if (mes) { try { et = MESES[Integer.parseInt(et.trim()) - 1]; } catch (RuntimeException ignorar) { /* se deja la clave */ } }
            destino.add(new GerenteInforme.FilaClave(et, num(v.getTotal()), dec(v.getMonto()), maxTotal, maxMonto, suma));
        }
    }

    private static long num(Number n) {
        return n == null ? 0L : n.longValue();
    }

    private static BigDecimal dec(Number n) {
        if (n == null) { return BigDecimal.ZERO; }
        return n instanceof BigDecimal ? (BigDecimal) n : new BigDecimal(n.toString());
    }
}
