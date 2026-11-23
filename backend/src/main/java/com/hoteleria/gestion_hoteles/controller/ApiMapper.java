package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.Pago;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import org.hibernate.Hibernate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialización limpia y plana de entidades JPA a JSON (Map) para evitar referencias circulares
 * y LazyInitializationException con spring.jpa.open-in-view=false.
 */
public final class ApiMapper {

    private ApiMapper() {
    }

    public static Map<String, Object> hotel(Hotel h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idHotel", h.getIdHotel());
        m.put("nombre", h.getNombre());
        m.put("direccion", h.getDireccion());
        m.put("ciudad", h.getCiudad());
        m.put("telefono", h.getTelefono());
        m.put("correo", h.getCorreo());
        m.put("activo", h.getActivo());
        m.put("totalHabitaciones", Hibernate.isInitialized(h.getHabitaciones()) ? h.getTotalHabitaciones() : null);
        return m;
    }

    public static Map<String, Object> tipo(TipoHabitacion t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idTipo", t.getIdTipo());
        m.put("nombre", t.getNombre());
        m.put("capacidad", t.getCapacidad());
        m.put("descripcion", t.getDescripcion());
        m.put("precioBase", t.getPrecioBase());
        m.put("totalHabitaciones", Hibernate.isInitialized(t.getHabitaciones()) ? t.getTotalHabitaciones() : null);
        return m;
    }

    public static Map<String, Object> habitacion(Habitacion ha) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idHabitacion", ha.getIdHabitacion());
        m.put("numero", ha.getNumero());
        m.put("piso", ha.getPiso());
        m.put("estado", ha.getEstado() != null ? ha.getEstado().name() : null);
        m.put("estadoCss", ha.getEstado() != null ? ha.getEstado().getCss() : null);
        if (ha.getHotel() != null && Hibernate.isInitialized(ha.getHotel())) {
            m.put("idHotel", ha.getHotel().getIdHotel());
            m.put("hotelNombre", ha.getHotel().getNombre());
            m.put("hotelCiudad", ha.getHotel().getCiudad());
            m.put("hotelActivo", ha.getHotel().getActivo());
        }
        if (ha.getTipo() != null && Hibernate.isInitialized(ha.getTipo())) {
            m.put("idTipo", ha.getTipo().getIdTipo());
            m.put("tipoNombre", ha.getTipo().getNombre());
            m.put("capacidad", ha.getTipo().getCapacidad());
            m.put("precioBase", ha.getTipo().getPrecioBase());
            m.put("tipoDescripcion", ha.getTipo().getDescripcion());
        }
        return m;
    }

    public static Map<String, Object> reservacion(Reservacion r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idReservacion", r.getIdReservacion());
        m.put("nombreHuesped", r.getNombreHuesped());
        m.put("telefono", r.getTelefono());
        m.put("correo", r.getCorreo());
        m.put("ciudadProcedencia", r.getCiudadProcedencia());
        m.put("fechaEntrada", r.getFechaEntrada());
        m.put("fechaSalida", r.getFechaSalida());
        m.put("adultos", r.getAdultos());
        m.put("ninos", r.getNinos());
        m.put("personas", r.getPersonas());
        m.put("noches", r.getNoches());
        m.put("precioNoche", r.getPrecioNoche());
        m.put("costoTotal", r.getCostoTotal());
        m.put("estado", r.getEstado() != null ? r.getEstado().name() : null);
        m.put("idUsuario", r.getIdUsuario());
        m.put("fechaCheckin", r.getFechaCheckin());
        m.put("checkinRealizado", r.isCheckinRealizado());
        m.put("origen", r.getOrigen());
        m.put("fechaRegistro", r.getFechaRegistro());
        if (r.getHabitacion() != null && Hibernate.isInitialized(r.getHabitacion())) {
            m.put("habitacion", habitacion(r.getHabitacion()));
        }
        return m;
    }

    public static Map<String, Object> estancia(Estancia e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idEstancia", e.getIdEstancia());
        m.put("huesped", e.getHuesped());
        m.put("identificacion", e.getIdentificacion());
        m.put("numHuespedes", e.getNumHuespedes());
        m.put("fechaEntrada", e.getFechaEntrada());
        m.put("fechaSalidaPrevista", e.getFechaSalidaPrevista());
        m.put("fechaSalidaReal", e.getFechaSalidaReal());
        m.put("precioNoche", e.getPrecioNoche());
        m.put("noches", e.getNoches());
        m.put("cargosExtra", e.getCargosExtra());
        m.put("total", e.getTotal());
        m.put("metodoPago", e.getMetodoPago() != null ? e.getMetodoPago().name() : null);
        m.put("activa", e.isActiva());
        m.put("vencida", e.isVencida());
        m.put("idUsuarioCheckin", e.getIdUsuarioCheckin());
        if (e.getReservacion() != null && Hibernate.isInitialized(e.getReservacion())) {
            m.put("idReservacion", e.getReservacion().getIdReservacion());
        }
        if (e.getHabitacion() != null && Hibernate.isInitialized(e.getHabitacion())) {
            m.put("habitacion", habitacion(e.getHabitacion()));
        }
        return m;
    }

    public static Map<String, Object> pago(Pago p) {
        if (p == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPago", p.getIdPago());
        m.put("monto", p.getMonto());
        m.put("metodo", p.getMetodo());
        m.put("marca", p.getMarca());
        m.put("ultimos4", p.getUltimos4());
        m.put("titular", p.getTitular());
        m.put("estado", p.getEstado() != null ? p.getEstado().name() : null);
        m.put("referencia", p.getReferencia());
        m.put("fecha", p.getFecha());
        return m;
    }

    public static Map<String, Object> usuario(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idUsuario", u.getIdUsuario());
        m.put("nombre", u.getNombre());
        m.put("correo", u.getCorreo());
        m.put("rol", u.getRol() != null ? u.getRol().name() : null);
        m.put("verified", u.isVerified());
        m.put("activo", u.isActivo());
        m.put("fechaRegistro", u.getFechaRegistro());
        return m;
    }
}
