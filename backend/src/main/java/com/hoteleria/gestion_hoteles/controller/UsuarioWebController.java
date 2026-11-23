package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** API REST del USUARIO (huésped): portada / catálogo y sus propias reservaciones. */
@RestController
@RequestMapping("/api/usuario")
public class UsuarioWebController {

    public record TarjetaTipo(String nombre, String descripcion, int capacidad, BigDecimal precio, String imagen) { }

    private final TipoHabitacionService tipoService;
    private final HotelService hotelService;
    private final ReservacionService reservacionService;

    public UsuarioWebController(TipoHabitacionService tipoService, HotelService hotelService,
                                ReservacionService reservacionService) {
        this.tipoService = tipoService;
        this.hotelService = hotelService;
        this.reservacionService = reservacionService;
    }

    @GetMapping("/inicio")
    public Map<String, Object> inicio() {
        Set<String> usadas = new HashSet<>();
        List<TarjetaTipo> tarjetas = new ArrayList<>();
        for (TipoHabitacion t : tipoService.listar()) {
            tarjetas.add(new TarjetaTipo(t.getNombre(), t.getDescripcion(), t.getCapacidad(), t.getPrecioBase(), imagenDe(t, usadas)));
        }
        Long yo = Seguridad.actual().orElseThrow().id();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipos", tarjetas);
        m.put("hoteles", hotelService.listarActivos().stream().map(ApiMapper::hotel).toList());
        m.put("misReservaciones", reservacionService.listarDeUsuario(yo).stream().limit(3).map(ApiMapper::reservacion).toList());
        return m;
    }

    @GetMapping("/mis-reservaciones")
    public List<Map<String, Object>> misReservaciones() {
        Long id = Seguridad.actual().orElseThrow().id();
        return reservacionService.listarDeUsuario(id).stream().map(ApiMapper::reservacion).toList();
    }

    /** Cancelar una reservación PROPIA (el servidor valida el dueño). */
    @PostMapping("/mis-reservaciones/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable Long id) {
        Reservacion r = reservacionService.buscar(id);
        Long yo = Seguridad.actual().orElseThrow().id();
        if (!yo.equals(r.getIdUsuario())) {
            throw new ReglaNegocioException("Esa reservación no está asociada a tu cuenta.");
        }
        if (r.getEstado() == EstadoReservacion.Cancelada) {
            throw new ReglaNegocioException("La reservación #" + id + " ya estaba cancelada.");
        }
        reservacionService.cancelar(id);
        return Map.of("mensaje", "Reservación #" + id + " cancelada.");
    }

    private static final List<String> CATALOGO_IMAGENES = List.of(
            "/img/hab_suite.jpg", "/img/hab_familiar_tres.jpg", "/img/hab_estandar_doble.jpg",
            "/img/hab_familiar.jpg", "/img/habitacion_sencilla.jpg", "/img/habitacion_familiar.jpg");

    private String imagenDe(TipoHabitacion t, Set<String> usadas) {
        String n = Normalizer.normalize(t.getNombre(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String preferida = n.contains("suite") ? CATALOGO_IMAGENES.get(0)
                : (n.contains("famil") || t.getCapacidad() >= 4) ? CATALOGO_IMAGENES.get(1)
                : CATALOGO_IMAGENES.get(2);
        if (usadas.add(preferida)) { return preferida; }
        for (String img : CATALOGO_IMAGENES) {
            if (usadas.add(img)) { return img; }
        }
        return preferida;
    }
}
