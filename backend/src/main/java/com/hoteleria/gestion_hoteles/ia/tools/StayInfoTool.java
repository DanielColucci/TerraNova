package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.CheckOutReciente;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.EstanciaActiva;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.EstanciaService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Historias 2 y 3 (check-in / check-out) en modo CONSULTA: huéspedes hospedados y últimos check-outs.
 * No registra ni modifica nada y NO expone datos personales (sin nombre ni identificación del huésped).
 */
@Component
public class StayInfoTool {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EstanciaService estanciaService;
    private final ToolAuditoria auditoria;

    public StayInfoTool(EstanciaService estanciaService, ToolAuditoria auditoria) {
        this.estanciaService = estanciaService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Lista las estancias EN CURSO (huéspedes hospedados ahora, ya hicieron check-in y todavía no check-out):
            hotel, habitación, tipo, número de huéspedes, fecha de entrada, salida prevista, si ya venció y precio por noche.
            No incluye nombres de huéspedes. Úsala para «quién está hospedado / cuántas estancias activas hay»,
            «qué habitaciones tienen check-out pendiente» o «qué estancias ya vencieron». Puede filtrarse por hotel.
            """)
    public Map<String, Object> consultarEstanciasActivas(
            @ToolParam(description = "Nombre o parte del nombre del hotel. Vacío para todos.", required = false)
            String hotel) {

        auditoria.registrar("consultarEstanciasActivas", "hotel=" + hotel);
        List<EstanciaActiva> resultado = estanciaService.activas().stream()
                .filter(e -> Filtros.vacio(hotel)
                        || Filtros.coincide(e.getHabitacion().getHotel().getNombre(), hotel, Filtros.IGNORAR_HOTEL))
                .map(e -> new EstanciaActiva(
                        e.getHabitacion().getHotel().getNombre(),
                        e.getHabitacion().getNumero(),
                        e.getHabitacion().getTipo().getNombre(),
                        e.getNumHuespedes() == null ? 0 : e.getNumHuespedes(),
                        e.getFechaEntrada() == null ? "" : e.getFechaEntrada().format(FECHA_HORA),
                        e.getFechaSalidaPrevista() == null ? "" : e.getFechaSalidaPrevista().toString(),
                        e.isVencida(),
                        e.getPrecioNoche()))
                .toList();
        return Filtros.respuesta(resultado, "No hay estancias en curso con esos criterios.");
    }

    @Tool(description = """
            Lista los últimos check-outs realizados (estancias ya cerradas): hotel, habitación, tipo, noches cobradas,
            total, método de pago y fecha de salida. No incluye nombres de huéspedes.
            Úsala para «cuáles fueron los últimos check-outs», «cuánto se cobró en las últimas salidas»
            o «cómo pagaron». Muestra como máximo los 8 más recientes.
            """)
    public Map<String, Object> consultarCheckOutsRecientes() {
        auditoria.registrar("consultarCheckOutsRecientes", "");
        List<CheckOutReciente> resultado = estanciaService.recientes().stream()
                .map(this::aCheckOut)
                .toList();
        return Filtros.respuesta(resultado, "Todavía no se han registrado check-outs.");
    }

    private CheckOutReciente aCheckOut(Estancia e) {
        return new CheckOutReciente(
                e.getHabitacion().getHotel().getNombre(),
                e.getHabitacion().getNumero(),
                e.getHabitacion().getTipo().getNombre(),
                e.getNoches() == null ? 0 : e.getNoches(),
                e.getTotal() == null ? BigDecimal.ZERO : e.getTotal(),
                e.getMetodoPago() == null ? "Sin dato" : e.getMetodoPago().name(),
                e.getFechaSalidaReal() == null ? "" : e.getFechaSalidaReal().format(FECHA_HORA));
    }
}
