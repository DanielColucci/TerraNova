package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import com.hoteleria.gestion_hoteles.seguridad.UsuarioActual;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Herramientas de reservaciones del rol USUARIO. La IA NO pide folio ni correo: la identidad sale del JWT
 * (SecurityContext), así que solo puede ver, crear, modificar y cancelar reservaciones de la cuenta autenticada.
 * Las escrituras se delegan en ReservationTool (mismas validaciones y misma confirmación «sí/no»), que además
 * vuelve a comprobar en el servidor que la reservación pertenezca al usuario. Un USUARIO no puede ELIMINAR.
 */
@Component
public class UsuarioReservationTool {

    public static final String SIN_RESERVACIONES =
            "Aún no tienes reservaciones, por lo que no puedo mostrarte información. ¿Deseas hacer una?";

    private final ReservacionService reservacionService;
    private final ReservationTool reservationTool;
    private final ToolAuditoria auditoria;

    public UsuarioReservationTool(ReservacionService reservacionService, ReservationTool reservationTool,
                                  ToolAuditoria auditoria) {
        this.reservacionService = reservacionService;
        this.reservationTool = reservationTool;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Muestra las reservaciones de la persona que está en el chat (la cuenta autenticada). NO necesita folio ni
            correo: ya se conoce al usuario. Si no tiene ninguna, devuelve el mensaje obligatorio que debes decir tal cual.
            Úsala para «mi reservación», «cuándo es mi estancia», «cuánto pago», etc.
            """)
    public Map<String, Object> misReservaciones() {
        auditoria.registrar("misReservaciones", "");
        UsuarioActual u = Seguridad.actual().orElse(null);
        if (u == null) {
            return Filtros.error("No hay una sesión válida. Pide al usuario que inicie sesión de nuevo.");
        }
        List<Reservacion> propias = reservacionService.listarDeUsuario(u.id());
        if (propias.isEmpty()) {
            return sinReservaciones();
        }
        return Filtros.respuesta(propias.stream().map(reservationTool::info).toList(), "");
    }

    @Tool(description = """
            CREA una reservación a nombre del usuario autenticado (su nombre y correo ya se conocen: NO los pidas).
            Obligatorios: hotel, teléfono (10 dígitos), fechaEntrada y fechaSalida (AAAA-MM-DD) y adultos.
            Opcionales: niños, tipo de habitación, numeroHabitacion y ciudad de procedencia. NO guarda nada al llamarla:
            deja la reservación pendiente y el sistema pide la confirmación al usuario.
            ANTES de llamarla debes haber consultado al huésped su preferencia (piso, ubicación, característica) o si
            acepta «cualquiera disponible»; pásala en «preferencia». Sin ella (y sin numeroHabitacion) la herramienta
            NO crea nada y devuelve las instrucciones para preguntarle.
            """)
    public Map<String, Object> crearMiReservacion(
            @ToolParam(description = "Nombre del hotel (ej. 'Hotel Centro').") String hotel,
            @ToolParam(description = "Teléfono de 10 dígitos.") String telefono,
            @ToolParam(description = "Fecha de entrada AAAA-MM-DD.") String fechaEntrada,
            @ToolParam(description = "Fecha de salida AAAA-MM-DD.") String fechaSalida,
            @ToolParam(description = "Número de adultos (mínimo 1).") Integer adultos,
            @ToolParam(description = "Número de niños. Omitir o 0 si no hay.", required = false) Integer ninos,
            @ToolParam(description = "Tipo de habitación (ej. 'Suite'). Opcional.", required = false) String tipo,
            @ToolParam(description = "Número de habitación concreto. Opcional.", required = false) String numeroHabitacion,
            @ToolParam(description = "Ciudad de procedencia. Opcional.", required = false) String ciudadProcedencia,
            @ToolParam(description = "Preferencia que el HUÉSPED expresó (piso, ubicación, característica) o 'cualquiera' si "
                    + "dijo que no tiene problema con cualquier habitación disponible. No la inventes.", required = false) String preferencia) {

        UsuarioActual u = Seguridad.actual().orElse(null);
        if (u == null) {
            return Filtros.error("No hay una sesión válida. Pide al usuario que inicie sesión de nuevo.");
        }
        boolean sinTipo = tipo == null || tipo.isBlank();
        boolean sinHabitacion = numeroHabitacion == null || numeroHabitacion.isBlank();
        boolean sinPreferencia = preferencia == null || preferencia.isBlank();
        if (sinHabitacion && sinPreferencia) {
            auditoria.registrar("crearMiReservacion", "falta preferencia");
            return Filtros.error("FALTAN_PREFERENCIAS: todavía NO se creó nada. "
                    + (sinTipo ? "El huésped no eligió tipo de habitación: resume brevemente las opciones disponibles "
                    + "(tipo, capacidad y precio) con la herramienta de disponibilidad y pregúntale cuál prefiere. " : "")
                    + "Pregúntale si tiene alguna preferencia (piso, ubicación o característica) o si no tiene problema en "
                    + "que se le asigne cualquiera disponible de esa categoría. Cuando responda, vuelve a llamar esta "
                    + "herramienta con preferencia (o con numeroHabitacion si pidió una concreta).");
        }
        return reservationTool.crearReservacion(hotel, u.nombre(), telefono, fechaEntrada, fechaSalida, adultos, ninos,
                tipo, numeroHabitacion, u.correo(), ciudadProcedencia);
    }

    @Tool(description = """
            MODIFICA la reservación del usuario autenticado (solo la suya): fechas, adultos, niños, habitación,
            teléfono o ciudad. El folio es opcional: si el usuario tiene una sola reservación activa se usa esa; si tiene
            varias, pregunta cuál (folio). Envía solo los campos que cambian. NO guarda al llamarla: queda pendiente de
            confirmación.
            """)
    public Map<String, Object> modificarMiReservacion(
            @ToolParam(description = "Folio de la reservación. Omitir si solo tiene una.", required = false) Long folio,
            @ToolParam(description = "Nueva fecha de entrada AAAA-MM-DD.", required = false) String fechaEntrada,
            @ToolParam(description = "Nueva fecha de salida AAAA-MM-DD.", required = false) String fechaSalida,
            @ToolParam(description = "Nuevo número de adultos.", required = false) Integer adultos,
            @ToolParam(description = "Nuevo número de niños.", required = false) Integer ninos,
            @ToolParam(description = "Nuevo número de habitación del mismo hotel.", required = false) String numeroHabitacion,
            @ToolParam(description = "Nuevo teléfono de 10 dígitos.", required = false) String telefono,
            @ToolParam(description = "Nueva ciudad de procedencia.", required = false) String ciudadProcedencia) {

        Object folioResuelto = resolverFolio(folio);
        if (folioResuelto instanceof Map<?, ?> error) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) error;
            return m;
        }
        return reservationTool.modificarReservacion((Long) folioResuelto, fechaEntrada, fechaSalida, adultos, ninos,
                numeroHabitacion, null, telefono, null, ciudadProcedencia);
    }

    @Tool(description = """
            CANCELA la reservación del usuario autenticado (solo la suya). El folio es opcional: si tiene una sola
            reservación activa se usa esa; si tiene varias, pregunta cuál. NO cancela al llamarla: queda pendiente de
            confirmación del usuario.
            """)
    public Map<String, Object> cancelarMiReservacion(
            @ToolParam(description = "Folio de la reservación. Omitir si solo tiene una.", required = false) Long folio) {

        Object folioResuelto = resolverFolio(folio);
        if (folioResuelto instanceof Map<?, ?> error) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) error;
            return m;
        }
        return reservationTool.cancelarReservacion((Long) folioResuelto);
    }

    /** Devuelve el folio a usar (Long) o un mapa de error/mensaje (regla A si no tiene reservaciones). */
    private Object resolverFolio(Long folio) {
        UsuarioActual u = Seguridad.actual().orElse(null);
        if (u == null) {
            return Filtros.error("No hay una sesión válida. Pide al usuario que inicie sesión de nuevo.");
        }
        List<Reservacion> activas = reservacionService.listarDeUsuario(u.id()).stream()
                .filter(r -> r.getEstado() != EstadoReservacion.Cancelada).toList();
        if (folio != null) {
            return folio;   // ReservationTool comprueba que sea suya
        }
        if (activas.isEmpty()) {
            return sinReservaciones();
        }
        if (activas.size() > 1) {
            return Filtros.error("El usuario tiene " + activas.size() + " reservaciones activas (folios: "
                    + String.join(", ", activas.stream().map(r -> "#" + r.getIdReservacion()).toList())
                    + "). Pregúntale cuál quiere usar.");
        }
        return activas.get(0).getIdReservacion();
    }

    private Map<String, Object> sinReservaciones() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tieneReservaciones", false);
        m.put("mensajeObligatorio", SIN_RESERVACIONES);
        m.put("instruccion", "Responde exactamente con el mensajeObligatorio. NO pidas folio ni otros datos.");
        return m;
    }
}
