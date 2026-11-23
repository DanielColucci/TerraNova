package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.ReservacionInfo;
import com.hoteleria.gestion_hoteles.ia.service.ConfirmacionAcciones;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import com.hoteleria.gestion_hoteles.seguridad.UsuarioActual;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reservaciones por IA: consultar, crear, modificar, cancelar y eliminar.
 * Las escrituras usan las MISMAS reglas que la aplicación (ReservacionService). Las Tools solo validan y dejan la
 * acción pendiente (ConfirmacionAcciones): se ejecuta cuando el usuario responde «sí». La IA nunca recibe teléfono ni correo.
 */
@Component
public class ReservationTool {

    private static final int LIMITE = 30;

    private final ReservacionService reservacionService;
    private final HotelService hotelService;
    private final TipoHabitacionService tipoService;
    private final ToolAuditoria auditoria;
    private final ConfirmacionAcciones confirmaciones;

    public ReservationTool(ReservacionService reservacionService, HotelService hotelService,
                           TipoHabitacionService tipoService, ToolAuditoria auditoria,
                           ConfirmacionAcciones confirmaciones) {
        this.reservacionService = reservacionService;
        this.hotelService = hotelService;
        this.tipoService = tipoService;
        this.auditoria = auditoria;
        this.confirmaciones = confirmaciones;
    }

    // ------------------------------------------------------------------ CONSULTAR

    private static final String MSG_SIN_FOLIO = "No se puede consultar sin un folio. Por seguridad NO se listan "
            + "reservaciones ni se buscan por nombre, hotel o fechas. No inventes datos ni llames otra herramienta: "
            + "pregúntale al usuario qué tipo de consulta quiere hacer sobre reservaciones (ver una reservación, "
            + "modificarla, cancelarla o eliminarla) y pídele el folio (número) de la reservación.";

    @Tool(description = """
            Consulta UNA reservación por su folio (número de reservación). El folio es obligatorio: NO existe un
            listado general ni búsqueda por nombre, hotel, estado o fechas. Si el usuario no da el folio, llama la
            herramienta sin folio: solo devolverá la reservación que se acaba de crear en ESTE chat; si no hay una,
            responde con la instrucción de preguntar al usuario qué consulta quiere hacer y cuál es su folio.
            No devuelve teléfono ni correo.
            """)
    public Map<String, Object> consultarReservaciones(
            @ToolParam(description = "Número (folio) de la reservación. Omitir solo si el usuario no lo sabe.", required = false) Long folio) {

        auditoria.registrar("consultarReservaciones", "folio=" + folio);
        try {
            if (folio != null) {
                return Filtros.respuesta(List.of(info(buscarPropia(folio))), "");
            }
            Optional<Long> reciente = confirmaciones.ultimoFolio(confirmaciones.conversacionActual());
            if (reciente.isEmpty()) {
                return Filtros.error(MSG_SIN_FOLIO);
            }
            Reservacion r;
            try {
                r = buscarPropia(reciente.get());
            } catch (RecursoNoEncontradoException e) {
                return Filtros.error(MSG_SIN_FOLIO);
            }
            Map<String, Object> resp = Filtros.respuesta(List.of(info(r)), "");
            resp.put("nota", "El usuario no dio folio: se muestra únicamente la reservación #" + r.getIdReservacion()
                    + ", creada en este chat. Para consultar otra, pídele su folio.");
            return resp;
        } catch (IllegalArgumentException | ReglaNegocioException | RecursoNoEncontradoException e) {
            return Filtros.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ CREAR

    @Tool(description = """
            CREA una reservación. Datos obligatorios: hotel, nombre del huésped, teléfono (10 dígitos), fechaEntrada y
            fechaSalida (AAAA-MM-DD) y adultos. Opcionales: tipo de habitación, numeroHabitacion (si no se da, se asigna
            la libre más barata), niños, correo y ciudad de procedencia. Si falta un dato obligatorio NO llames la
            herramienta: pídeselo al usuario. NO guarda nada: valida disponibilidad y deja la reservación pendiente de
            confirmación; el sistema le muestra el resumen al usuario y la guarda cuando éste responde «sí».
            """)
    public Map<String, Object> crearReservacion(
            @ToolParam(description = "Nombre del hotel (ej. 'Hotel Centro').") String hotel,
            @ToolParam(description = "Nombre completo del huésped.") String nombreHuesped,
            @ToolParam(description = "Teléfono de 10 dígitos.") String telefono,
            @ToolParam(description = "Fecha de entrada AAAA-MM-DD.") String fechaEntrada,
            @ToolParam(description = "Fecha de salida AAAA-MM-DD (no se cobra esa noche).") String fechaSalida,
            @ToolParam(description = "Número de adultos (mínimo 1).") Integer adultos,
            @ToolParam(description = "Número de niños. Omitir o 0 si no hay.", required = false) Integer ninos,
            @ToolParam(description = "Tipo de habitación (ej. 'Suite'). Opcional.", required = false) String tipo,
            @ToolParam(description = "Número de habitación concreto (ej. '101'). Opcional.", required = false) String numeroHabitacion,
            @ToolParam(description = "Correo electrónico del huésped. Opcional.", required = false) String correo,
            @ToolParam(description = "Ciudad de procedencia. Opcional.", required = false) String ciudadProcedencia) {

        auditoria.registrar("crearReservacion", "hotel=" + hotel + ", entrada=" + fechaEntrada
                + ", salida=" + fechaSalida + ", adultos=" + adultos + ", ninos=" + ninos + ", tipo=" + tipo
                + ", habitacion=" + numeroHabitacion);
        try {
            if (Filtros.vacio(hotel) || Filtros.vacio(nombreHuesped) || Filtros.vacio(telefono)
                    || Filtros.vacio(fechaEntrada) || Filtros.vacio(fechaSalida) || adultos == null) {
                return Filtros.error("Faltan datos obligatorios (hotel, nombre del huésped, teléfono, fechas y adultos). "
                        + "Pídeselos al usuario antes de continuar.");
            }
            LocalDate entrada = Filtros.fecha(fechaEntrada, "fecha de entrada");
            LocalDate salida = Filtros.fecha(fechaSalida, "fecha de salida");
            int ni = ninos == null ? 0 : ninos;
            reservacionService.validarHuesped(nombreHuesped, telefono, correo);

            Hotel h = hotelUnico(hotel);
            Long idTipo = Filtros.vacio(tipo) ? null : tipoPorNombre(tipo).getIdTipo();
            List<Habitacion> libres = reservacionService.habitacionesLibres(h.getIdHotel(), idTipo, adultos + ni, entrada, salida);
            Habitacion elegida = elegir(libres, numeroHabitacion);

            long noches = ChronoUnit.DAYS.between(entrada, salida);
            String resumen = "CREAR reservación en «" + h.getNombre() + "», habitación " + elegida.getNumero() + " ("
                    + elegida.getTipo().getNombre() + ", máx. " + elegida.getTipo().getCapacidad() + " personas)\n"
                    + "• Huésped: " + nombreHuesped.trim() + "\n"
                    + "• Fechas: del " + entrada + " al " + salida + " (" + noches + " noches)\n"
                    + "• Personas: " + adultos + " adultos y " + ni + " niños\n"
                    + "• Costo: " + pesos(elegida.getTipo().getPrecioBase()) + " por noche, total "
                    + pesos(elegida.getTipo().getPrecioBase().multiply(java.math.BigDecimal.valueOf(noches)));

            final Long idHabitacion = elegida.getIdHabitacion();
            final String conversacion = confirmaciones.conversacionActual();   // el hilo del modelo ya no existirá al confirmar
            // Si quien crea es un USUARIO, la reservación queda a su nombre (se captura aquí: al confirmar es otra petición)
            final Long idUsuarioCreador = Seguridad.actual().filter(u -> !u.esPersonal()).map(UsuarioActual::id).orElse(null);
            final boolean requierePago = idUsuarioCreador != null;
            String resumenConfirmacion = resumen + (requierePago
                    ? "\n• Después de confirmar tienes 15 minutos para pagar en línea; si no pagas, la reservación se cancela."
                    : "");
            confirmaciones.solicitar("crearReservacion", "crear", resumenConfirmacion, () -> {
                Reservacion r = reservacionService.crear(idHabitacion, nombreHuesped, telefono, correo,
                        ciudadProcedencia, entrada, salida, adultos, ni, "IA", idUsuarioCreador, requierePago);
                confirmaciones.recordarFolio(conversacion, r.getIdReservacion());
                String enlacePago = requierePago
                        ? "\n\n💳 La habitación queda retenida 15 minutos. Para confirmarla, paga aquí: /pago/" + r.getIdReservacion()
                        : "";
                return "✅ Reservación #" + r.getIdReservacion() + " creada: " + r.getHabitacion().getHotel().getNombre()
                        + ", habitación " + r.getHabitacion().getNumero() + ", a nombre de " + r.getNombreHuesped()
                        + ", del " + r.getFechaEntrada() + " al " + r.getFechaSalida() + " (" + r.getNoches()
                        + " noches). Total: " + pesos(r.getCostoTotal()) + "." + enlacePago;
            });
            return pendiente(resumen);
        } catch (IllegalArgumentException | ReglaNegocioException | RecursoNoEncontradoException e) {
            return Filtros.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ MODIFICAR

    @Tool(description = """
            MODIFICA una reservación existente (no cancelada) identificada por su folio. Solo envía los campos que
            cambian: fechas, adultos, niños, número de habitación (mismo hotel), nombre, teléfono, correo o ciudad.
            Valida disponibilidad y capacidad con las nuevas fechas y recalcula el costo. NO guarda nada: deja el cambio
            pendiente de confirmación (el sistema se lo muestra al usuario y lo aplica cuando responde «sí»).
            Si no tienes el folio, pídeselo al usuario (no se puede buscar por nombre).
            """)
    public Map<String, Object> modificarReservacion(
            @ToolParam(description = "Folio (número) de la reservación a modificar.") Long folio,
            @ToolParam(description = "Nueva fecha de entrada AAAA-MM-DD.", required = false) String fechaEntrada,
            @ToolParam(description = "Nueva fecha de salida AAAA-MM-DD.", required = false) String fechaSalida,
            @ToolParam(description = "Nuevo número de adultos.", required = false) Integer adultos,
            @ToolParam(description = "Nuevo número de niños.", required = false) Integer ninos,
            @ToolParam(description = "Nuevo número de habitación del mismo hotel (ej. '102').", required = false) String numeroHabitacion,
            @ToolParam(description = "Nuevo nombre del huésped.", required = false) String nombreHuesped,
            @ToolParam(description = "Nuevo teléfono de 10 dígitos.", required = false) String telefono,
            @ToolParam(description = "Nuevo correo.", required = false) String correo,
            @ToolParam(description = "Nueva ciudad de procedencia.", required = false) String ciudadProcedencia) {

        auditoria.registrar("modificarReservacion", "folio=" + folio + ", entrada=" + fechaEntrada
                + ", salida=" + fechaSalida + ", adultos=" + adultos + ", ninos=" + ninos + ", habitacion=" + numeroHabitacion);
        try {
            Reservacion actual = buscarPropia(folio);
            if (actual.getEstado() == EstadoReservacion.Cancelada) {
                return Filtros.error("La reservación #" + folio + " está cancelada y ya no se puede modificar. "
                        + "Ofrece crear una reservación nueva.");
            }
            LocalDate entrada = Filtros.vacio(fechaEntrada) ? null : Filtros.fecha(fechaEntrada, "fecha de entrada");
            LocalDate salida = Filtros.vacio(fechaSalida) ? null : Filtros.fecha(fechaSalida, "fecha de salida");

            Long idHabitacionNueva = null;
            if (!Filtros.vacio(numeroHabitacion)) {
                idHabitacionNueva = reservacionService.idHabitacion(actual.getHabitacion().getHotel().getIdHotel(), numeroHabitacion)
                        .orElseThrow(() -> new ReglaNegocioException("El hotel «" + actual.getHabitacion().getHotel().getNombre()
                                + "» no tiene la habitación " + numeroHabitacion + "."));
            }
            String tel = Filtros.vacio(telefono) ? null : telefono;
            String cor = Filtros.vacio(correo) ? null : correo;
            String nom = Filtros.vacio(nombreHuesped) ? null : nombreHuesped;
            String ciu = Filtros.vacio(ciudadProcedencia) ? null : ciudadProcedencia;

            String cambios = (entrada != null ? "• Nueva entrada: " + entrada + "\n" : "")
                    + (salida != null ? "• Nueva salida: " + salida + "\n" : "")
                    + (adultos != null ? "• Adultos: " + adultos + "\n" : "")
                    + (ninos != null ? "• Niños: " + ninos + "\n" : "")
                    + (idHabitacionNueva != null ? "• Habitación: " + numeroHabitacion + "\n" : "")
                    + (nom != null ? "• Nombre: " + nom + "\n" : "")
                    + (tel != null ? "• Teléfono nuevo\n" : "")
                    + (cor != null ? "• Correo nuevo\n" : "")
                    + (ciu != null ? "• Ciudad: " + ciu + "\n" : "");
            if (cambios.isEmpty()) {
                return Filtros.error("No indicaste ningún cambio. Pregunta al usuario qué quiere modificar de la reservación #" + folio + ".");
            }
            String resumen = "MODIFICAR la reservación #" + folio + " de " + actual.getNombreHuesped() + " en «"
                    + actual.getHabitacion().getHotel().getNombre() + "» (hoy: habitación " + actual.getHabitacion().getNumero()
                    + ", del " + actual.getFechaEntrada() + " al " + actual.getFechaSalida() + ", " + actual.getAdultos()
                    + " adultos y " + actual.getNinos() + " niños)\nCambios:\n" + cambios
                    + "El costo se recalcula con la tarifa de la habitación.";

            // Valida ya (sin guardar) para no pedir confirmación de un cambio imposible.
            validarSinGuardar(actual, entrada, salida, adultos, ninos, idHabitacionNueva);

            final Long idHab = idHabitacionNueva;
            confirmaciones.solicitar("modificarReservacion", "modificar", resumen, () -> {
                Reservacion r = reservacionService.modificar(folio, idHab, nom, tel, cor, ciu, entrada, salida, adultos, ninos);
                return "✅ Reservación #" + r.getIdReservacion() + " modificada: " + r.getHabitacion().getHotel().getNombre()
                        + ", habitación " + r.getHabitacion().getNumero() + ", del " + r.getFechaEntrada() + " al "
                        + r.getFechaSalida() + " (" + r.getNoches() + " noches), " + r.getAdultos() + " adultos y "
                        + r.getNinos() + " niños. Total: " + pesos(r.getCostoTotal()) + ".";
            });
            return pendiente(resumen);
        } catch (IllegalArgumentException | ReglaNegocioException | RecursoNoEncontradoException e) {
            return Filtros.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ CANCELAR / ELIMINAR

    @Tool(description = """
            CANCELA una reservación por su folio (queda en el historial y libera la habitación para esas fechas).
            Es lo recomendado cuando el usuario dice «cancela». NO cancela al llamarla: deja la acción pendiente de
            confirmación y el sistema la ejecuta cuando el usuario responde «sí».
            """)
    public Map<String, Object> cancelarReservacion(
            @ToolParam(description = "Folio (número) de la reservación.") Long folio) {

        auditoria.registrar("cancelarReservacion", "folio=" + folio);
        try {
            Reservacion r = buscarPropia(folio);
            if (r.getEstado() == EstadoReservacion.Cancelada) {
                return Filtros.error("La reservación #" + folio + " ya estaba cancelada.");
            }
            String resumen = "CANCELAR la reservación #" + folio + " de " + r.getNombreHuesped()
                    + " en «" + r.getHabitacion().getHotel().getNombre() + "», habitación " + r.getHabitacion().getNumero()
                    + ", del " + r.getFechaEntrada() + " al " + r.getFechaSalida() + ".";
            confirmaciones.solicitar("cancelarReservacion", "cancelar", resumen,
                    () -> "✅ Reservación #" + reservacionService.cancelar(folio).getIdReservacion() + " cancelada.");
            return pendiente(resumen);
        } catch (ReglaNegocioException | RecursoNoEncontradoException e) {
            return Filtros.error(e.getMessage());
        }
    }

    @Tool(description = """
            ELIMINA DEFINITIVAMENTE una reservación por su folio (se borra el registro, no se puede deshacer). Úsala solo
            cuando el usuario pida explícitamente «eliminar» o «borrar»; si dice «cancelar» usa cancelarReservacion.
            NO elimina al llamarla: deja la acción pendiente de confirmación y el sistema la ejecuta cuando el usuario
            responde «sí».
            """)
    public Map<String, Object> eliminarReservacion(
            @ToolParam(description = "Folio (número) de la reservación.") Long folio) {

        auditoria.registrar("eliminarReservacion", "folio=" + folio);
        if (Seguridad.actual().map(UsuarioActual::esRecepcionista).orElse(false)) {
            return Filtros.error("Recepción no puede eliminar reservaciones definitivamente; solo cancelarlas. "
                    + "Pide al administrador que la elimine.");
        }
        try {
            Reservacion r = buscarPropia(folio);
            String resumen = "ELIMINAR DEFINITIVAMENTE la reservación #" + folio + " de "
                    + r.getNombreHuesped() + " en «" + r.getHabitacion().getHotel().getNombre() + "», habitación "
                    + r.getHabitacion().getNumero() + ", del " + r.getFechaEntrada() + " al " + r.getFechaSalida()
                    + ". No se puede deshacer.";
            confirmaciones.solicitar("eliminarReservacion", "eliminar", resumen,
                    () -> "✅ Reservación #" + reservacionService.eliminar(folio).getIdReservacion()
                            + " eliminada definitivamente.");
            return pendiente(resumen);
        } catch (ReglaNegocioException | RecursoNoEncontradoException e) {
            return Filtros.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ utilidades

    private Hotel hotelUnico(String nombre) {
        List<Hotel> coinciden = hotelService.listar().stream()
                .filter(h -> Filtros.coincide(h.getNombre(), nombre, Filtros.IGNORAR_HOTEL)).toList();
        if (coinciden.isEmpty()) {
            throw new ReglaNegocioException("No se encontró ningún hotel llamado '" + nombre + "'. Hoteles registrados: "
                    + String.join(", ", hotelService.listar().stream().map(Hotel::getNombre).toList()) + ".");
        }
        if (coinciden.size() > 1) {
            throw new ReglaNegocioException("'" + nombre + "' coincide con varios hoteles ("
                    + String.join(", ", coinciden.stream().map(Hotel::getNombre).toList())
                    + "). Pregunta al usuario en cuál quiere reservar.");
        }
        return coinciden.get(0);
    }

    private TipoHabitacion tipoPorNombre(String nombre) {
        return tipoService.listar().stream()
                .filter(t -> Filtros.coincide(t.getNombre(), nombre, Filtros.IGNORAR_TIPO))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("No se encontró el tipo de habitación '" + nombre
                        + "'. Tipos registrados: " + String.join(", ", tipoService.listar().stream().map(TipoHabitacion::getNombre).toList()) + "."));
    }

    private Habitacion elegir(List<Habitacion> libres, String numero) {
        if (libres.isEmpty()) {
            throw new ReglaNegocioException("No hay habitaciones libres en ese hotel para esas fechas y esa cantidad de personas. "
                    + "Sugiere otras fechas, otro tipo o otro hotel.");
        }
        if (Filtros.vacio(numero)) {
            return libres.get(0);
        }
        return libres.stream().filter(h -> h.getNumero().equalsIgnoreCase(numero.trim())).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("La habitación " + numero + " no está libre en esas fechas o no admite "
                        + "esa cantidad de personas. Habitaciones libres: "
                        + String.join(", ", libres.stream().limit(8).map(Habitacion::getNumero).toList()) + "."));
    }

    /** Valida un cambio sin guardarlo (para no pedir confirmación de algo imposible). */
    private void validarSinGuardar(Reservacion actual, LocalDate entrada, LocalDate salida,
                                   Integer adultos, Integer ninos, Long idHabitacionNueva) {
        int personas = (adultos != null ? adultos : actual.getAdultos()) + (ninos != null ? ninos : actual.getNinos());
        boolean cambiaFechas = (entrada != null && !entrada.equals(actual.getFechaEntrada()))
                || (salida != null && !salida.equals(actual.getFechaSalida()));
        boolean cambiaHabitacion = idHabitacionNueva != null
                && !idHabitacionNueva.equals(actual.getHabitacion().getIdHabitacion());
        if (!cambiaFechas && !cambiaHabitacion) {
            // Solo cambian datos del huésped o el número de personas: basta con revisar la capacidad actual.
            if (personas > actual.getHabitacion().getTipo().getCapacidad()) {
                throw new ReglaNegocioException("La habitación " + actual.getHabitacion().getNumero() + " admite máximo "
                        + actual.getHabitacion().getTipo().getCapacidad() + " personas.");
            }
            return;
        }
        LocalDate e = entrada != null ? entrada : actual.getFechaEntrada();
        LocalDate s = salida != null ? salida : actual.getFechaSalida();
        Long idHab = idHabitacionNueva != null ? idHabitacionNueva : actual.getHabitacion().getIdHabitacion();
        // Cuenta como libre si ya es de esta misma reservación: se compara contra las libres + la actual.
        List<Habitacion> libres = reservacionService.habitacionesLibres(
                actual.getHabitacion().getHotel().getIdHotel(), null, personas, e, s);
        boolean propiaMismasFechas = idHab.equals(actual.getHabitacion().getIdHabitacion())
                && e.equals(actual.getFechaEntrada()) && s.equals(actual.getFechaSalida());
        boolean ok = propiaMismasFechas || libres.stream().anyMatch(h -> h.getIdHabitacion().equals(idHab));
        if (!ok && !idHab.equals(actual.getHabitacion().getIdHabitacion())) {
            throw new ReglaNegocioException("La habitación pedida no está libre del " + e + " al " + s + " o no admite " + personas + " personas.");
        }
        if (!ok) {
            // Misma habitación con otras fechas: puede chocar solo con SU propia reserva; se valida al guardar.
            if (personas > actual.getHabitacion().getTipo().getCapacidad()) {
                throw new ReglaNegocioException("La habitación " + actual.getHabitacion().getNumero() + " admite máximo "
                        + actual.getHabitacion().getTipo().getCapacidad() + " personas.");
            }
        }
    }

    /** Respuesta de la herramienta: la acción NO se ha hecho; el sistema pedirá la confirmación al usuario. */
    private Map<String, Object> pendiente(String resumen) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("requiereConfirmacion", true);
        r.put("resumen", resumen);
        r.put("instruccion", "Validado, pero NO se ha guardado nada todavía. El sistema mostrará el resumen al usuario y le "
                + "pedirá confirmar; cuando responda «sí» lo ejecutará él mismo. Tú no pidas confirmación ni llames de nuevo "
                + "esta herramienta. Responde solo: «Revisa el resumen y confirma».");
        return r;
    }

    /**
     * Busca la reservación y, si quien pregunta es un USUARIO (no administrador), exige que sea suya.
     * Es la barrera del servidor para la regla «el usuario solo ve/edita/cancela SU reservación».
     */
    private Reservacion buscarPropia(Long folio) {
        Reservacion r = reservacionService.buscar(folio);
        Seguridad.actual().filter(u -> !u.esPersonal()).ifPresent(u -> {
            if (!u.id().equals(r.getIdUsuario())) {
                throw new ReglaNegocioException("Esa reservación no está asociada a tu cuenta, por lo que no puedo "
                        + "mostrarla ni modificarla.");
            }
        });
        return r;
    }

    ReservacionInfo info(Reservacion r) {
        Habitacion ha = r.getHabitacion();
        return new ReservacionInfo(r.getIdReservacion(), ha.getHotel().getNombre(), ha.getNumero(), ha.getTipo().getNombre(),
                r.getNombreHuesped(), r.getFechaEntrada().toString(), r.getFechaSalida().toString(), r.getNoches(),
                r.getAdultos(), r.getNinos(), r.getCostoTotal(), r.getEstado().name(), r.getOrigen());
    }

    private EstadoReservacion estadoReservacion(String texto) {
        String n = Filtros.normalizar(texto);
        if (n.startsWith("confirm")) { return EstadoReservacion.Confirmada; }
        if (n.contains("pago")) { return EstadoReservacion.Pendiente_Pago; }
        if (n.startsWith("pendien")) { return EstadoReservacion.Pendiente; }
        if (n.startsWith("cancel")) { return EstadoReservacion.Cancelada; }
        return null;
    }

    private String pesos(java.math.BigDecimal valor) {
        return String.format(java.util.Locale.US, "$%,.2f", valor);
    }
}
