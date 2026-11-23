package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.dto.EjecutivoResumen;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.ConteoHotel;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.HabitacionInfo;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.TipoInfo;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Tipos de habitación, habitaciones y conteos por hotel (mismos datos que los CRUD). Solo lectura. */
@Component
public class RoomInfoTool {

    private static final int LIMITE = 60;

    private final TipoHabitacionService tipoService;
    private final HabitacionService habitacionService;
    private final HotelService hotelService;
    private final ConsultaService consultaService;
    private final ToolAuditoria auditoria;

    public RoomInfoTool(TipoHabitacionService tipoService, HabitacionService habitacionService,
                        HotelService hotelService, ConsultaService consultaService, ToolAuditoria auditoria) {
        this.tipoService = tipoService;
        this.habitacionService = habitacionService;
        this.hotelService = hotelService;
        this.consultaService = consultaService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Consulta los tipos de habitación que ofrece la cadena: nombre, capacidad en personas, descripción,
            precio base por noche y cuántas habitaciones usan cada tipo.
            Úsala cuando pregunten qué tipos o categorías de habitación existen, cuánto cuesta un tipo,
            qué capacidad tiene o qué incluye. Para un tipo concreto envía su nombre; vacío para listarlos todos.
            """)
    public Map<String, Object> consultarTiposHabitacion(
            @ToolParam(description = "Nombre del tipo (ej. 'Suite', 'Doble'). Vacío para listar todos.", required = false)
            String nombreTipo) {

        auditoria.registrar("consultarTiposHabitacion", "nombreTipo=" + nombreTipo);
        List<TipoHabitacion> todos = tipoService.listar();
        List<TipoInfo> resultado = todos.stream()
                .filter(t -> Filtros.vacio(nombreTipo) || Filtros.coincide(t.getNombre(), nombreTipo, Filtros.IGNORAR_TIPO))
                .map(t -> new TipoInfo(t.getIdTipo(), t.getNombre(), t.getCapacidad(), t.getDescripcion(),
                        t.getPrecioBase(), t.getTotalHabitaciones()))
                .toList();
        String registrados = String.join(", ", todos.stream().map(TipoHabitacion::getNombre).toList());
        return Filtros.respuesta(resultado,
                "No se encontró ese tipo de habitación. Tipos registrados: " + registrados + ".");
    }

    @Tool(description = """
            Lista habitaciones concretas con su hotel, número, piso, tipo, capacidad, precio base y estado
            (Disponible, Ocupada o Mantenimiento). Puede filtrarse por hotel, por tipo y por estado.
            Úsala para preguntas como «qué habitaciones tiene el Hotel Centro», «cuáles están ocupadas»,
            «cuáles están en mantenimiento» o «qué habitaciones de tipo Suite hay».
            Para saber CUÁNTAS habitaciones hay por hotel usa mejor contarHabitacionesPorHotel.
            Para buscar habitaciones disponibles con presupuesto, ciudad o fechas usa buscarHabitacionesDisponibles.
            """)
    public Map<String, Object> consultarHabitaciones(
            @ToolParam(description = "Nombre o parte del nombre del hotel (ej. 'Ejecutivo'). Vacío para todos.", required = false)
            String hotel,
            @ToolParam(description = "Nombre del tipo de habitación (ej. 'Doble'). Vacío para todos.", required = false)
            String tipo,
            @ToolParam(description = "Estado: Disponible, Ocupada o Mantenimiento. Vacío para todos.", required = false)
            String estado) {

        auditoria.registrar("consultarHabitaciones", "hotel=" + hotel + ", tipo=" + tipo + ", estado=" + estado);

        String estadoFiltro = null;
        if (!Filtros.vacio(estado)) {
            estadoFiltro = Filtros.estadoOficial(estado);
            if (estadoFiltro == null) {
                return Filtros.error("Estado no permitido: '" + estado + "'. Los estados válidos son Disponible, Ocupada y Mantenimiento.");
            }
        }
        if (!Filtros.vacio(hotel) && hotelService.listar().stream()
                .noneMatch(h -> Filtros.coincide(h.getNombre(), hotel, Filtros.IGNORAR_HOTEL))) {
            return Filtros.error("No se encontró ningún hotel llamado '" + hotel + "'. Hoteles registrados: "
                    + String.join(", ", hotelService.listar().stream().map(Hotel::getNombre).toList()) + ".");
        }
        if (!Filtros.vacio(tipo) && tipoService.listar().stream()
                .noneMatch(t -> Filtros.coincide(t.getNombre(), tipo, Filtros.IGNORAR_TIPO))) {
            return Filtros.error("No se encontró el tipo de habitación '" + tipo + "'. Tipos registrados: "
                    + String.join(", ", tipoService.listar().stream().map(TipoHabitacion::getNombre).toList()) + ".");
        }

        final String estadoBuscado = estadoFiltro;
        List<HabitacionInfo> todas = habitacionService.listar().stream()
                .filter(h -> Filtros.vacio(hotel) || Filtros.coincide(h.getHotel().getNombre(), hotel, Filtros.IGNORAR_HOTEL))
                .filter(h -> Filtros.vacio(tipo) || Filtros.coincide(h.getTipo().getNombre(), tipo, Filtros.IGNORAR_TIPO))
                .filter(h -> estadoBuscado == null || h.getEstado().name().equals(estadoBuscado))
                .map(this::aInfo)
                .toList();

        List<HabitacionInfo> visibles = todas.size() > LIMITE ? todas.subList(0, LIMITE) : todas;
        Map<String, Object> r = Filtros.respuesta(visibles, "No hay habitaciones que cumplan esos filtros.");
        r.put("total", todas.size());
        if (todas.size() > LIMITE) {
            r.put("nota", "Se muestran solo las primeras " + LIMITE + " de " + todas.size() + " habitaciones.");
        }
        return r;
    }

    @Tool(description = """
            Cuenta las habitaciones de cada hotel: total, disponibles, ocupadas y en mantenimiento, indicando si
            el hotel está activo. Úsala para «cuántas habitaciones tiene cada hotel», «cuántas disponibles hay por hotel»,
            «cuántas están ocupadas» o «cuántas están en mantenimiento». Para un hotel concreto envía su nombre.
            """)
    public Map<String, Object> contarHabitacionesPorHotel(
            @ToolParam(description = "Nombre o parte del nombre del hotel. Vacío para todos los hoteles.", required = false)
            String hotel) {

        auditoria.registrar("contarHabitacionesPorHotel", "hotel=" + hotel);
        List<EjecutivoResumen.FilaHotel> filas = consultaService.ejecutivo().getHoteles();
        List<ConteoHotel> resultado = filas.stream()
                .filter(f -> Filtros.vacio(hotel) || Filtros.coincide(f.getHotel(), hotel, Filtros.IGNORAR_HOTEL))
                .map(f -> new ConteoHotel(f.getHotel(), f.getCiudad(), f.isActivo(), f.getTotal(),
                        f.getDisponibles(), f.getOcupadas(), f.getMantenimiento()))
                .toList();
        return Filtros.respuesta(resultado, "No se encontró ningún hotel con ese nombre. Hoteles registrados: "
                + String.join(", ", filas.stream().map(EjecutivoResumen.FilaHotel::getHotel).toList()) + ".");
    }

    private HabitacionInfo aInfo(Habitacion h) {
        return new HabitacionInfo(h.getIdHabitacion(), h.getHotel().getNombre(),
                Boolean.TRUE.equals(h.getHotel().getActivo()), h.getNumero(), h.getPiso(),
                h.getTipo().getNombre(), h.getTipo().getCapacidad(), h.getTipo().getPrecioBase(),
                h.getEstado().name());
    }
}
