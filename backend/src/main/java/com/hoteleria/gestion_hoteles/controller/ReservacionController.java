package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.dto.ReservacionForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.PagoRepository;
import com.hoteleria.gestion_hoteles.service.EstanciaService;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** API REST de reservaciones (solo ADMINISTRADOR; las reglas viven en ReservacionService). */
@RestController
@RequestMapping("/api/reservaciones")
public class ReservacionController {

    public record OpcionHabitacion(Long id, String etiqueta) { }

    public record HabitacionNoDisponible(String hotel, String numero, String tipo, String estado,
                                         String huesped, LocalDate liberaEl, String sugerencia) { }

    private final ReservacionService reservacionService;
    private final HabitacionService habitacionService;
    private final EstanciaService estanciaService;
    private final PagoRepository pagoRepository;

    public ReservacionController(ReservacionService reservacionService,
                                 HabitacionService habitacionService,
                                 EstanciaService estanciaService,
                                 PagoRepository pagoRepository) {
        this.reservacionService = reservacionService;
        this.habitacionService = habitacionService;
        this.estanciaService = estanciaService;
        this.pagoRepository = pagoRepository;
    }

    @GetMapping
    public Map<String, Object> lista(@RequestParam(defaultValue = "proximas") String vista,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "0") int anio,
                                     @RequestParam(required = false) String q) {
        if (!ReservacionService.VISTAS.contains(vista)) {
            vista = "proximas";
        }
        String busqueda = q == null ? "" : q.trim();
        var pag = reservacionService.pagina(vista, anio, busqueda, pagina);
        if (pag.getTotalPages() > 0 && pagina >= pag.getTotalPages()) {
            pag = reservacionService.pagina(vista, anio, busqueda, pag.getTotalPages() - 1);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservaciones", pag.getContent().stream().map(ApiMapper::reservacion).toList());
        m.put("pagina", pag.getNumber());
        m.put("totalPaginas", pag.getTotalPages());
        m.put("totalElementos", pag.getTotalElements());
        m.put("vista", vista);
        m.put("anio", anio);
        m.put("q", busqueda);
        m.put("conteos", reservacionService.conteos(anio, busqueda));
        m.put("anios", reservacionService.aniosConReservaciones());
        m.put("pagadas", pagoRepository.idsReservacionPagadas());
        return m;
    }

    @GetMapping("/opciones")
    public Map<String, Object> opciones(@RequestParam(required = false) Long idHabitacionActual) {
        return construirOpciones(idHabitacionActual);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        Reservacion r = reservacionService.buscar(id);
        Map<String, Object> m = construirOpciones(r.getHabitacion().getIdHabitacion());
        m.put("reservacion", ApiMapper.reservacion(r));
        m.put("cancelada", r.getEstado() == EstadoReservacion.Cancelada);
        return m;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody ReservacionForm form) {
        Reservacion r = reservacionService.crear(form.getIdHabitacion(), form.getNombreHuesped(),
                form.getTelefono(), form.getCorreo(), form.getCiudadProcedencia(), form.getFechaEntrada(),
                form.getFechaSalida(), form.getAdultos(), form.getNinos(), "WEB");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Reservación #" + r.getIdReservacion() + " creada para "
                + r.getNombreHuesped() + " (" + r.getHabitacion().getHotel().getNombre() + ", habitación "
                + r.getHabitacion().getNumero() + ").");
        m.put("reservacion", ApiMapper.reservacion(r));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable Long id, @Valid @RequestBody ReservacionForm form) {
        Reservacion actual = reservacionService.buscar(id);
        if (actual.getEstado() == EstadoReservacion.Cancelada) {
            throw new ReglaNegocioException("La reservación #" + id + " está cancelada y ya no se puede modificar.");
        }
        Reservacion r = reservacionService.modificar(id, form.getIdHabitacion(), form.getNombreHuesped(),
                form.getTelefono(), form.getCorreo(),
                form.getCiudadProcedencia() == null ? "" : form.getCiudadProcedencia(),
                form.getFechaEntrada(), form.getFechaSalida(), form.getAdultos(), form.getNinos());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Reservación #" + r.getIdReservacion() + " actualizada. Nuevo total: $"
                + String.format(Locale.US, "%,.2f", r.getCostoTotal()) + ".");
        m.put("reservacion", ApiMapper.reservacion(r));
        return m;
    }

    @PostMapping("/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable Long id) {
        reservacionService.cancelar(id);
        return Map.of("mensaje", "Reservación #" + id + " cancelada. La habitación quedó libre para esas fechas.");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Long id) {
        reservacionService.eliminar(id);
        return Map.of("mensaje", "Reservación #" + id + " eliminada definitivamente.");
    }

    /** Reutilizado también por recepción al registrar reservaciones en mostrador. */
    Map<String, Object> construirOpciones(Long idHabitacionActual) {
        List<OpcionHabitacion> opciones = new ArrayList<>();
        List<Habitacion> noDisponibles = new ArrayList<>();
        for (Habitacion ha : habitacionService.listar()) {
            if (!Boolean.TRUE.equals(ha.getHotel().getActivo())) {
                if (idHabitacionActual != null && idHabitacionActual.equals(ha.getIdHabitacion())) {
                    opciones.add(opcion(ha));
                }
                continue;
            }
            boolean esLaActual = idHabitacionActual != null && idHabitacionActual.equals(ha.getIdHabitacion());
            if (ha.getEstado() == EstadoHabitacion.Disponible || esLaActual) {
                opciones.add(opcion(ha));
            } else {
                noDisponibles.add(ha);
            }
        }

        Map<Long, Estancia> activasPorHabitacion = new HashMap<>();
        for (Estancia e : estanciaService.activas()) {
            activasPorHabitacion.put(e.getHabitacion().getIdHabitacion(), e);
        }
        List<HabitacionNoDisponible> proximas = new ArrayList<>();
        for (Habitacion ha : noDisponibles) {
            Estancia e = activasPorHabitacion.get(ha.getIdHabitacion());
            String hotel = ha.getHotel().getNombre();
            String tipo = ha.getTipo().getNombre();
            if (ha.getEstado() == EstadoHabitacion.Mantenimiento) {
                proximas.add(new HabitacionNoDisponible(hotel, ha.getNumero(), tipo, "Mantenimiento", null, null,
                        "En mantenimiento: sin fecha estimada. Consulta con mantenimiento."));
            } else if (e != null && e.getFechaSalidaPrevista() != null) {
                LocalDate salida = e.getFechaSalidaPrevista();
                long dias = ChronoUnit.DAYS.between(LocalDate.now(), salida);
                String cuando = dias < 0 ? "salida vencida hace " + (-dias) + (dias == -1 ? " día" : " días")
                        + " (confirma si el huésped ya salió)"
                        : dias == 0 ? "sale hoy"
                        : dias == 1 ? "sale mañana" : "sale en " + dias + " días";
                proximas.add(new HabitacionNoDisponible(hotel, ha.getNumero(), tipo, "Ocupada", e.getHuesped(), salida,
                        "Ocupada: " + cuando + ". Libre desde el " + salida
                                + " más el tiempo de limpieza o mantenimiento."));
            } else {
                proximas.add(new HabitacionNoDisponible(hotel, ha.getNumero(), tipo, "Ocupada", null, null,
                        "Ocupada sin salida registrada: fecha de liberación desconocida."));
            }
        }
        proximas.sort((a, b) -> {
            if (a.liberaEl() == null && b.liberaEl() == null) { return 0; }
            if (a.liberaEl() == null) { return 1; }
            if (b.liberaEl() == null) { return -1; }
            return a.liberaEl().compareTo(b.liberaEl());
        });

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("habitaciones", opciones);
        m.put("proximasLiberaciones", proximas);
        m.put("hoy", LocalDate.now());
        return m;
    }

    private OpcionHabitacion opcion(Habitacion ha) {
        return new OpcionHabitacion(ha.getIdHabitacion(),
                ha.getHotel().getNombre() + " · Hab. " + ha.getNumero() + " · " + ha.getTipo().getNombre()
                        + " (" + ha.getTipo().getCapacidad() + " pers.) · $"
                        + String.format(Locale.US, "%,.2f", ha.getTipo().getPrecioBase()) + "/noche");
    }
}
