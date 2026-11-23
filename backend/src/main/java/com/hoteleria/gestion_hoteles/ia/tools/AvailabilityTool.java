package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.dto.ResultadoBusqueda;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.Disponible;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Historia de usuario 1: búsqueda avanzada de disponibilidad.
 * Reutiliza ConsultaService (misma consulta SQL que la pantalla /busqueda). Solo lectura.
 */
@Component
public class AvailabilityTool {

    private static final int LIMITE = 40;

    private final ConsultaService consultaService;
    private final HotelService hotelService;
    private final TipoHabitacionService tipoService;
    private final ToolAuditoria auditoria;

    public AvailabilityTool(ConsultaService consultaService, HotelService hotelService,
                            TipoHabitacionService tipoService, ToolAuditoria auditoria) {
        this.consultaService = consultaService;
        this.hotelService = hotelService;
        this.tipoService = tipoService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Busca habitaciones DISPONIBLES (estado Disponible, solo hoteles activos) y las ordena por precio.
            Puede filtrar por ciudad, hotel, tipo de habitación, capacidad mínima de personas y precio máximo por noche.
            Si se indican fechaEntrada y fechaSalida (formato AAAA-MM-DD) calcula el total estimado (precio por noche x noches).
            IMPORTANTE: esta herramienta muestra lo libre AHORA (estado Disponible) y las fechas solo sirven para
            estimar el costo. Para saber si una habitación está libre en fechas futuras, o para reservar, usa las
            herramientas de reservaciones. Úsala para «qué habitaciones hay disponibles»,
            «habitaciones para 4 personas», «las más baratas», «disponibles en Toluca» o «cuánto costaría del 10 al 12 de noviembre».
            """)
    public Map<String, Object> buscarHabitacionesDisponibles(
            @ToolParam(description = "Ciudad (ej. 'Toluca', 'Ciudad de México'). Vacío para cualquiera.", required = false)
            String ciudad,
            @ToolParam(description = "Nombre o parte del nombre del hotel. Vacío para todos.", required = false)
            String hotel,
            @ToolParam(description = "Tipo de habitación (ej. 'Suite'). Vacío para todos.", required = false)
            String tipo,
            @ToolParam(description = "Capacidad mínima en personas (número entero). Omitir si no se indica.", required = false)
            Integer capacidadMinima,
            @ToolParam(description = "Precio máximo por noche en pesos. Omitir si no se indica.", required = false)
            Double precioMaximoPorNoche,
            @ToolParam(description = "Fecha de llegada AAAA-MM-DD. Omitir si no se indica.", required = false)
            String fechaEntrada,
            @ToolParam(description = "Fecha de salida AAAA-MM-DD (no se cobra esa noche). Omitir si no se indica.", required = false)
            String fechaSalida) {

        auditoria.registrar("buscarHabitacionesDisponibles", "ciudad=" + ciudad + ", hotel=" + hotel + ", tipo=" + tipo
                + ", capacidadMinima=" + capacidadMinima + ", precioMax=" + precioMaximoPorNoche
                + ", entrada=" + fechaEntrada + ", salida=" + fechaSalida);

        // --- Validación de fechas ---
        LocalDate entrada = null;
        LocalDate salida = null;
        boolean hayEntrada = !Filtros.vacio(fechaEntrada);
        boolean haySalida = !Filtros.vacio(fechaSalida);
        if (hayEntrada != haySalida) {
            return Filtros.error("Faltan datos: para estimar el total se necesitan la fecha de llegada y la de salida. "
                    + "Pide al usuario la fecha que falta (formato AAAA-MM-DD).");
        }
        if (hayEntrada) {
            try {
                entrada = Filtros.fecha(fechaEntrada, "fecha de llegada");
                salida = Filtros.fecha(fechaSalida, "fecha de salida");
            } catch (IllegalArgumentException e) {
                return Filtros.error(e.getMessage());
            }
            if (entrada.isBefore(LocalDate.now())) {
                return Filtros.error("La fecha de llegada no puede ser anterior a hoy (" + LocalDate.now() + ").");
            }
            if (!salida.isAfter(entrada)) {
                return Filtros.error("La fecha de salida debe ser posterior a la de llegada.");
            }
        }
        if (capacidadMinima != null && capacidadMinima < 0) {
            return Filtros.error("La capacidad mínima no puede ser negativa.");
        }
        if (precioMaximoPorNoche != null && precioMaximoPorNoche < 0) {
            return Filtros.error("El precio máximo no puede ser negativo.");
        }

        // --- Resolución de hotel y tipo por nombre ---
        List<Hotel> hoteles = hotelService.listar();
        List<Long> idsHotel = new ArrayList<>();
        if (!Filtros.vacio(hotel)) {
            hoteles.stream()
                    .filter(h -> Filtros.coincide(h.getNombre(), hotel, Filtros.IGNORAR_HOTEL))
                    .forEach(h -> idsHotel.add(h.getIdHotel()));
            if (idsHotel.isEmpty()) {
                return Filtros.error("No se encontró ningún hotel llamado '" + hotel + "'. Hoteles registrados: "
                        + String.join(", ", hoteles.stream().map(Hotel::getNombre).toList()) + ".");
            }
        }
        Long idTipo = null;
        if (!Filtros.vacio(tipo)) {
            List<TipoHabitacion> tipos = tipoService.listar().stream()
                    .filter(t -> Filtros.coincide(t.getNombre(), tipo, Filtros.IGNORAR_TIPO))
                    .toList();
            if (tipos.isEmpty()) {
                return Filtros.error("No se encontró el tipo de habitación '" + tipo + "'. Tipos registrados: "
                        + String.join(", ", tipoService.listar().stream().map(TipoHabitacion::getNombre).toList()) + ".");
            }
            idTipo = tipos.get(0).getIdTipo();
        }

        String ciudadFiltro = Filtros.vacio(ciudad) ? null : ciudad.trim();
        BigDecimal precioMax = precioMaximoPorNoche == null ? null : BigDecimal.valueOf(precioMaximoPorNoche);

        // --- Misma consulta SQL de la pantalla de búsqueda (una vez por hotel coincidente) ---
        List<ResultadoBusqueda> encontrados = new ArrayList<>();
        if (idsHotel.isEmpty()) {
            encontrados.addAll(consultaService.buscarDisponibilidad(ciudadFiltro, null, idTipo,
                    capacidadMinima, precioMax, entrada, salida));
        } else {
            for (Long id : idsHotel) {
                encontrados.addAll(consultaService.buscarDisponibilidad(ciudadFiltro, id, idTipo,
                        capacidadMinima, precioMax, entrada, salida));
            }
            encontrados.sort(Comparator.comparing(ResultadoBusqueda::getPrecio)
                    .thenComparing(ResultadoBusqueda::getHotel)
                    .thenComparing(ResultadoBusqueda::getHabitacion));
        }

        List<Disponible> todos = encontrados.stream()
                .map(r -> new Disponible(r.getHotel(), r.getCiudad(), r.getHabitacion(), r.getPiso(), r.getTipo(),
                        r.getCapacidad(), r.getPrecio(), r.getTotalEstimado()))
                .toList();
        List<Disponible> visibles = todos.size() > LIMITE ? todos.subList(0, LIMITE) : todos;

        Map<String, Object> r = Filtros.respuesta(visibles,
                "No hay habitaciones disponibles con esos criterios. Sugiere ampliar los filtros.");
        r.put("total", todos.size());
        if (todos.size() > LIMITE) {
            r.put("nota", "Se muestran solo las " + LIMITE + " más baratas de " + todos.size() + " habitaciones.");
        }
        if (entrada != null) {
            r.put("noches", java.time.temporal.ChronoUnit.DAYS.between(entrada, salida));
        }
        return r;
    }
}
