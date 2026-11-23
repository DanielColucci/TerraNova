package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.dto.GerenteInforme;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fase 6 · Herramienta del GERENTE (solo lectura): mismo informe que /gerente/informes, así las cifras coinciden. */
@Component
public class GerenteInformeTool {

    private final ConsultaService consultaService;
    private final ToolAuditoria auditoria;

    public GerenteInformeTool(ConsultaService consultaService, ToolAuditoria auditoria) {
        this.consultaService = consultaService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Informe gerencial completo con todos los datos crudos del panel del gerente: ingresos aprobados (total, pagos,
            ticket promedio, ingreso por noche, serie por día/mes y comparativa contra el periodo anterior), ocupación
            actual e histórica por hotel (habitaciones, ocupadas hoy, noches vendidas/disponibles), reservaciones por
            estado (confirmadas, pendientes, pago vencido, canceladas, montos y % pérdidas), tipos de habitación más
            rentables, evolución anual, temporada por mes, procedencia de huéspedes, origen de reservaciones (Web vs IA)
            y métodos de pago. Úsala para cualquier consulta de métricas o datos crudos del gerente.
            Fechas AAAA-MM-DD (opcionales: sin ellas usa el mes en curso). hotel opcional: nombre de la sede.
            """)
    public Map<String, Object> informeGerencial(
            @ToolParam(description = "Fecha inicial AAAA-MM-DD. Omitir = primer día del mes.", required = false) String desde,
            @ToolParam(description = "Fecha final AAAA-MM-DD. Omitir = hoy.", required = false) String hasta,
            @ToolParam(description = "Nombre del hotel (sede). Omitir = todas.", required = false) String hotel) {

        auditoria.registrar("informeGerencial", "desde=" + desde + " hasta=" + hasta + " hotel=" + hotel);
        try {
            LocalDate d = Filtros.vacio(desde) ? null : Filtros.fecha(desde, "desde");
            LocalDate h = Filtros.vacio(hasta) ? null : Filtros.fecha(hasta, "hasta");
            Long idHotel = null;
            if (!Filtros.vacio(hotel)) {
                List<Hotel> coinciden = consultaService.sedes().stream()
                        .filter(x -> Filtros.coincide(x.getNombre(), hotel, Filtros.IGNORAR_HOTEL)).toList();
                if (coinciden.size() != 1) {
                    return Filtros.error(coinciden.isEmpty() ? "No encontré ese hotel." : "Hay varios hoteles con ese nombre; sé más específico.");
                }
                idHotel = coinciden.get(0).getIdHotel();
            }
            GerenteInforme g = consultaService.informeGerente(d, h, idHotel);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("periodo", g.desde + " a " + g.hasta + " (" + g.dias + " días)");
            m.put("sede", g.hotelNombre);
            m.put("ingresosAprobados", g.ingresosTotal);
            m.put("pagosAprobados", g.pagosAprobados);
            m.put("ticketPromedio", g.getTicketPromedio());
            m.put("ingresoPorNoche", g.getIngresoPorNoche());
            m.put("ocupacionActualPct", g.getPctOcupacionActual());
            m.put("ocupadasAhora", g.ocupadasAhora);
            m.put("habitacionesTotal", g.habitacionesTotal);
            m.put("ocupacionPeriodoPct", g.getPctOcupacionHistorica());
            m.put("nochesVendidas", g.nochesVendidas);
            m.put("nochesDisponibles", g.nochesDisponibles);
            m.put("estanciaPromedioNoches", g.promedioEstancia);

            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("periodoAnterior", (g.desdePrevio != null && g.hastaPrevio != null) ? g.desdePrevio + " a " + g.hastaPrevio : null);
            comp.put("ingresosPeriodoAnterior", g.ingresosPrevio);
            comp.put("variacionIngresosPct", g.getVariacionIngresos());
            m.put("comparativaPeriodoAnterior", comp);

            Map<String, Object> e = new LinkedHashMap<>();
            e.put("total", g.estados.total);
            e.put("confirmadas", g.estados.confirmadas);
            e.put("pctConfirmadas", g.estados.getPctConfirmadas());
            e.put("pendientes", g.estados.pendientes + g.estados.pendientePago);
            e.put("pctPendientes", g.estados.getPctPendientes());
            e.put("pagoVencido", g.estados.vencidas);
            e.put("pctVencidas", g.estados.getPctVencidas());
            e.put("canceladas", g.estados.canceladas);
            e.put("pctCanceladas", g.estados.getPctCanceladas());
            e.put("pctPerdidas", g.estados.getPctPerdidas());
            e.put("canceladasSinPago", g.estados.canceladasSinPago);
            e.put("montoConfirmado", g.estados.montoConfirmado);
            e.put("montoCancelado", g.estados.montoCancelado);
            m.put("reservacionesPorEstado", e);

            Map<String, Object> ingresosSerie = new LinkedHashMap<>();
            ingresosSerie.put("agrupacion", g.agrupacion);
            ingresosSerie.put("detalle", g.periodo.stream().map(p -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("periodo", p.etiqueta);
                row.put("ingresos", p.ingresos);
                return row;
            }).toList());
            m.put("ingresosPorPeriodo", ingresosSerie);

            m.put("hoteles", g.hoteles.stream().map(f -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("hotel", f.hotel);
                row.put("ciudad", f.ciudad);
                row.put("habitaciones", f.habitaciones);
                row.put("ocupadasAhora", f.ocupadasAhora);
                row.put("ocupacionActualPct", f.getPctOcupacionActual());
                row.put("nochesVendidas", f.nochesVendidas);
                row.put("nochesDisponibles", f.nochesDisponibles);
                row.put("ocupacionPeriodoPct", f.getPctOcupacionHistorica());
                row.put("pagos", f.pagos);
                row.put("ingresos", f.ingresos);
                row.put("participacionIngresoPct", f.pctIngreso);
                return row;
            }).toList());

            m.put("tiposMasRentables", g.tipos.stream().map(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tipo", t.tipo);
                row.put("ingresos", t.ingresos);
                row.put("pagos", t.pagos);
                row.put("reservaciones", t.reservaciones);
                row.put("estanciaPromedioNoches", t.promNoches);
                row.put("ticketPromedio", t.getTicketPromedio());
                row.put("participacionPct", t.pctIngreso);
                return row;
            }).toList());

            m.put("evolucionAnual", g.anios.stream().map(a -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("anio", a.etiqueta);
                row.put("pagos", a.total);
                row.put("ingresos", a.monto);
                return row;
            }).toList());

            m.put("temporadaPorMes", g.meses.stream().map(mes -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("mes", mes.etiqueta);
                row.put("pagos", mes.total);
                row.put("ingresos", mes.monto);
                return row;
            }).toList());

            m.put("procedenciaHuespedes", g.procedencia.stream().map(pr -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ciudad", pr.etiqueta);
                row.put("reservaciones", pr.total);
                row.put("monto", pr.monto);
                return row;
            }).toList());

            m.put("origenReservaciones", g.origen.stream().map(or -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("origen", or.etiqueta);
                row.put("reservaciones", or.total);
                row.put("porcentajePct", or.pctParte);
                row.put("monto", or.monto);
                return row;
            }).toList());

            m.put("metodosPago", g.metodos.stream().map(mp -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("metodo", mp.etiqueta);
                row.put("pagos", mp.total);
                row.put("ingresos", mp.monto);
                return row;
            }).toList());

            m.put("nota", "Datos crudos del panel gerencial (solo lectura). Ingresos = pagos Aprobados del periodo. El gerente no realiza ni modifica reservaciones.");
            return m;
        } catch (RuntimeException ex) {
            return Filtros.error(ex.getMessage() == null ? "No se pudo generar el informe." : ex.getMessage());
        }
    }
}
