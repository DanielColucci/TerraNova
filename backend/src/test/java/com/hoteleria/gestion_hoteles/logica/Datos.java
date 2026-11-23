package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;

import java.math.BigDecimal;

/** Constructores de objetos de prueba (evitan repetir setters en cada test). */
final class Datos {

    private Datos() {
    }

    static Hotel hotel(Long id, String nombre, boolean activo) {
        Hotel h = new Hotel();
        h.setIdHotel(id);
        h.setNombre(nombre);
        h.setDireccion("Av. Independencia 210");
        h.setCiudad("Toluca");
        h.setTelefono("722 214 5500");
        h.setCorreo("hotel@terranova.com");
        h.setActivo(activo);
        return h;
    }

    static TipoHabitacion tipo(Long id, String nombre, int capacidad, String precio) {
        TipoHabitacion t = new TipoHabitacion();
        t.setIdTipo(id);
        t.setNombre(nombre);
        t.setCapacidad(capacidad);
        t.setDescripcion("Tipo " + nombre);
        t.setPrecioBase(new BigDecimal(precio));
        return t;
    }

    static Habitacion habitacion(Long id, Hotel hotel, TipoHabitacion tipo, String numero, EstadoHabitacion estado) {
        Habitacion h = new Habitacion();
        h.setIdHabitacion(id);
        h.setHotel(hotel);
        h.setTipo(tipo);
        h.setNumero(numero);
        h.setPiso(1);
        h.setEstado(estado);
        return h;
    }
}
