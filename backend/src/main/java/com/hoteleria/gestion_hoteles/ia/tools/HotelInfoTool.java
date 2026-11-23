package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.HotelInfo;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.HotelService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Catálogo de hoteles (mismos datos que el CRUD de Hoteles). Solo lectura. */
@Component
public class HotelInfoTool {

    private final HotelService hotelService;
    private final ToolAuditoria auditoria;

    public HotelInfoTool(HotelService hotelService, ToolAuditoria auditoria) {
        this.hotelService = hotelService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Consulta los hoteles registrados en la cadena TerraNova: id, nombre, dirección, ciudad, teléfono,
            correo, si está activo o inactivo y cuántas habitaciones tiene.
            Úsala cuando pregunten qué hoteles existen, por los datos de contacto o la ubicación de un hotel,
            si un hotel está activo o inactivo, o cuántas habitaciones tiene un hotel en total.
            Si dan el nombre de un hotel, envíalo en nombreHotel; si no, déjalo vacío para listar todos.
            """)
    public Map<String, Object> consultarHoteles(
            @ToolParam(description = "Nombre o parte del nombre del hotel (ej. 'Centro'). Vacío para listar todos.", required = false)
            String nombreHotel) {

        auditoria.registrar("consultarHoteles", "nombreHotel=" + nombreHotel);
        List<Hotel> todos = hotelService.listar();
        List<HotelInfo> resultado = todos.stream()
                .filter(h -> Filtros.vacio(nombreHotel) || Filtros.coincide(h.getNombre(), nombreHotel, Filtros.IGNORAR_HOTEL))
                .map(h -> new HotelInfo(h.getIdHotel(), h.getNombre(), h.getDireccion(), h.getCiudad(),
                        h.getTelefono(), h.getCorreo(), Boolean.TRUE.equals(h.getActivo()), h.getTotalHabitaciones()))
                .toList();

        String registrados = String.join(", ", todos.stream().map(Hotel::getNombre).toList());
        return Filtros.respuesta(resultado,
                "No se encontró ningún hotel con ese nombre. Hoteles registrados: " + registrados + ".");
    }
}
