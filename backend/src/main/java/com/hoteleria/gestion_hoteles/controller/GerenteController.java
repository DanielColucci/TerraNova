package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.service.ConsultaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST del GERENTE (y ADMINISTRADOR): dashboard agregado, informe completo y exportación CSV.
 * Toda agregación y cálculo se ejecuta en SQL/backend (ConsultaService.informeGerente).
 */
@RestController
@RequestMapping("/api/gerente")
public class GerenteController {

    private static final List<String> TABS =
            List.of("resumen", "reservaciones", "ocupacion", "rentabilidad", "tendencias", "huespedes");

    private final ConsultaService consultaService;

    public GerenteController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) String desde,
                                         @RequestParam(required = false) String hasta,
                                         @RequestParam(required = false) Long hotel,
                                         @RequestParam(required = false) String tab) {
        Map<String, Object> m = cargar(desde, hasta, hotel);
        m.put("tab", tab != null && TABS.contains(tab) ? tab : "resumen");
        return m;
    }

    @GetMapping("/informes")
    public Map<String, Object> informes(@RequestParam(required = false) String desde,
                                        @RequestParam(required = false) String hasta,
                                        @RequestParam(required = false) Long hotel) {
        return cargar(desde, hasta, hotel);
    }

    private Map<String, Object> cargar(String desde, String hasta, Long hotel) {
        LocalDate d = fecha(desde);
        LocalDate h = fecha(hasta);
        LocalDate hoy = LocalDate.now();
        var g = consultaService.informeGerente(d, h, hotel);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hoy", hoy);
        m.put("inicioMes", hoy.withDayOfMonth(1));
        m.put("hace30", hoy.minusDays(29));
        m.put("inicioAnio", hoy.withDayOfYear(1));
        m.put("ahora", LocalDateTime.now());
        m.put("informe", g);
        m.put("desde", g.desde);
        m.put("hasta", g.hasta);
        m.put("hotelSel", g.idHotel == null ? 0L : g.idHotel);
        m.put("sedes", consultaService.sedes().stream().map(ApiMapper::hotel).toList());
        return m;
    }

    private static LocalDate fecha(String s) {
        if (s == null || s.isBlank()) { return null; }
        try { return LocalDate.parse(s.trim()); } catch (DateTimeParseException e) { return null; }
    }

    /** Exporta el informe a CSV (UTF-8 con BOM para que Excel respete los acentos). */
    @GetMapping("/informes.csv")
    public ResponseEntity<byte[]> informeCsv(@RequestParam(required = false) String desde,
                                             @RequestParam(required = false) String hasta,
                                             @RequestParam(required = false) Long hotel) {
        var g = consultaService.informeGerente(fecha(desde), fecha(hasta), hotel);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Informe gerencial TerraNova\n");
        sb.append("Sede;").append(csv(g.hotelNombre)).append("\nPeriodo;").append(g.desde).append(";").append(g.hasta).append("\n\n");
        sb.append("HOTELES\nHotel;Ciudad;Habitaciones;Ocupacion actual %;Ocupacion periodo %;Noches vendidas;Pagos;Ingresos\n");
        g.hoteles.forEach(f -> sb.append(csv(f.hotel)).append(';').append(csv(f.ciudad)).append(';').append(f.habitaciones).append(';')
                .append(f.getPctOcupacionActual()).append(';').append(f.getPctOcupacionHistorica()).append(';')
                .append(f.nochesVendidas).append(';').append(f.pagos).append(';').append(f.ingresos).append('\n'));
        sb.append("\nTIPOS DE HABITACION\nTipo;Reservaciones;Estancia promedio (noches);Pagos;Ingresos;Participacion %\n");
        g.tipos.forEach(t -> sb.append(csv(t.tipo)).append(';').append(t.reservaciones).append(';').append(t.promNoches).append(';')
                .append(t.pagos).append(';').append(t.ingresos).append(';').append(t.pctIngreso).append('\n'));
        sb.append("\nINGRESOS POR ").append(g.agrupacion.toUpperCase()).append("\nPeriodo;Ingresos\n");
        g.periodo.forEach(p -> sb.append(csv(p.etiqueta)).append(';').append(p.ingresos).append('\n'));
        sb.append("\nRESERVACIONES POR ESTADO\nConfirmadas;").append(g.estados.confirmadas)
                .append("\nPendientes;").append(g.estados.pendientes + g.estados.pendientePago)
                .append("\nPago vencido;").append(g.estados.vencidas)
                .append("\nCanceladas;").append(g.estados.canceladas)
                .append("\nTotal;").append(g.estados.total).append('\n');
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informe-terranova-" + g.desde + "_" + g.hasta + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String s) {
        if (s == null) { return ""; }
        String v = s.replace("\"", "\"\"");
        return v.matches(".*[;\"\n].*") ? "\"" + v + "\"" : v;
    }
}
