package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.dto.CheckOutForm;
import com.hoteleria.gestion_hoteles.dto.ReservacionForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.EstadoPago;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.MetodoPago;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.PagoRepository;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import com.hoteleria.gestion_hoteles.service.EstanciaService;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API REST del módulo de recepción (roles RECEPCIONISTA y ADMINISTRADOR). */
@RestController
@RequestMapping("/api/recepcion")
public class RecepcionController {

    public record CheckInRequest(MetodoPago metodoPago) { }

    private final EstanciaService estanciaService;
    private final HabitacionService habitacionService;
    private final ReservacionService reservacionService;
    private final PagoRepository pagoRepository;
    private final ReservacionController reservacionController;

    public RecepcionController(EstanciaService estanciaService,
                               HabitacionService habitacionService,
                               ReservacionService reservacionService,
                               PagoRepository pagoRepository,
                               ReservacionController reservacionController) {
        this.estanciaService = estanciaService;
        this.habitacionService = habitacionService;
        this.reservacionService = reservacionService;
        this.pagoRepository = pagoRepository;
        this.reservacionController = reservacionController;
    }

    @GetMapping("/panel")
    public Map<String, Object> panel() {
        var habitaciones = habitacionService.listar();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("llegadas", reservacionService.llegadasPendientes().stream().map(ApiMapper::reservacion).toList());
        m.put("estancias", estanciaService.activas().stream().map(ApiMapper::estancia).toList());
        m.put("habitaciones", habitaciones.stream().map(ApiMapper::habitacion).toList());
        m.put("nDisponibles", habitaciones.stream().filter(h -> h.getEstado() == EstadoHabitacion.Disponible).count());
        m.put("nOcupadas", habitaciones.stream().filter(h -> h.getEstado() == EstadoHabitacion.Ocupada).count());
        m.put("nMantenimiento", habitaciones.stream().filter(h -> h.getEstado() == EstadoHabitacion.Mantenimiento).count());
        m.put("hoy", LocalDate.now());
        return m;
    }

    // ------------------------------------------------------------------ CHECK-IN

    @GetMapping("/checkin")
    public Map<String, Object> checkIn(@RequestParam(required = false) String q) {
        boolean buscando = q != null && !q.isBlank();
        var lista = buscando ? reservacionService.buscarParaCheckIn(q) : reservacionService.llegadasPendientes();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("q", q == null ? "" : q);
        m.put("buscando", buscando);
        m.put("resultados", lista.stream().map(ApiMapper::reservacion).toList());
        m.put("hoy", LocalDate.now());
        return m;
    }

    @GetMapping("/checkin/{id}")
    public Map<String, Object> checkInDetalle(@PathVariable Long id) {
        Reservacion r = reservacionService.buscar(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservacion", ApiMapper.reservacion(r));
        m.put("pagoPendiente", r.getEstado() == EstadoReservacion.Pendiente
                || r.getEstado() == EstadoReservacion.Pendiente_Pago);
        m.put("yaIngreso", reservacionService.tieneCheckOut(id));
        m.put("pagadoEnLinea", pagoRepository.existsByReservacion_IdReservacionAndEstado(id, EstadoPago.Aprobado));
        m.put("metodos", Arrays.stream(MetodoPago.values()).map(Enum::name).toList());
        m.put("hoy", LocalDate.now());
        return m;
    }

    @PostMapping("/checkin/{id}")
    public Map<String, Object> checkInConfirmar(@PathVariable Long id,
                                                @RequestBody(required = false) CheckInRequest req) {
        Long idUsuario = Seguridad.actual().map(u -> u.id()).orElse(null);
        MetodoPago metodo = req == null ? null : req.metodoPago();
        Estancia e = estanciaService.registrarCheckInReservacion(id, metodo, idUsuario);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Check-in registrado: habitación " + e.getHabitacion().getNumero() + " entregada a "
                + e.getHuesped() + ". Ya aparece en «Huéspedes en el hotel».");
        m.put("estancia", ApiMapper.estancia(e));
        return m;
    }

    // ------------------------------------------------------------------ CHECK-OUT

    @GetMapping("/checkout")
    public Map<String, Object> listaCheckOut() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enCurso", reservacionService.enCurso().stream().map(ApiMapper::reservacion).toList());
        m.put("estancias", estanciaService.activas().stream().map(ApiMapper::estancia).toList());
        m.put("recientes", estanciaService.recientes().stream().map(ApiMapper::estancia).toList());
        m.put("sinRegistro", estanciaService.ocupadasSinRegistro().stream().map(ApiMapper::habitacion).toList());
        m.put("hoy", LocalDate.now());
        return m;
    }

    @GetMapping("/checkout/reservacion/{id}")
    public Map<String, Object> confirmarCheckOutReservacion(@PathVariable Long id) {
        Reservacion r = reservacionService.buscar(id);
        if (reservacionService.tieneCheckOut(id)) {
            throw new ReglaNegocioException("La reservación #" + id + " ya tiene check-out registrado.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservacion", ApiMapper.reservacion(r));
        m.put("pagadoEnLinea", pagoRepository.existsByReservacion_IdReservacionAndEstado(id, EstadoPago.Aprobado));
        m.put("metodos", Arrays.stream(MetodoPago.values()).map(Enum::name).toList());
        return m;
    }

    @PostMapping("/checkout/reservacion/{id}")
    public Map<String, Object> registrarCheckOutReservacion(@PathVariable Long id,
                                                            @Valid @RequestBody CheckOutForm form) {
        Estancia folio = reservacionService.registrarCheckOut(id, form);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Check-out registrado. Folio cerrado y habitación "
                + folio.getHabitacion().getNumero() + " enviada a limpieza (Mantenimiento).");
        m.put("idEstancia", folio.getIdEstancia());
        m.put("estancia", ApiMapper.estancia(folio));
        return m;
    }

    @GetMapping("/checkout/{id}")
    public Map<String, Object> confirmarCheckOut(@PathVariable Long id) {
        Estancia e = estanciaService.buscar(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estancia", ApiMapper.estancia(e));
        m.put("activa", e.isActiva());
        m.put("noches", e.isActiva() ? estanciaService.nochesTranscurridas(e) : e.getNoches());
        m.put("importe", e.isActiva() ? estanciaService.importeHospedaje(e)
                : e.getPrecioNoche().multiply(BigDecimal.valueOf(e.getNoches())));
        m.put("metodos", Arrays.stream(MetodoPago.values()).map(Enum::name).toList());
        return m;
    }

    @PostMapping("/checkout/{id}")
    public Map<String, Object> registrarCheckOut(@PathVariable Long id, @Valid @RequestBody CheckOutForm form) {
        Estancia cerrada = estanciaService.registrarCheckOut(id, form);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Check-out registrado. Folio cerrado y habitación "
                + cerrada.getHabitacion().getNumero() + " ahora está en " + cerrada.getHabitacion().getEstado() + ".");
        m.put("idEstancia", cerrada.getIdEstancia());
        m.put("estancia", ApiMapper.estancia(cerrada));
        return m;
    }

    @PostMapping("/checkout/regularizar/{idHabitacion}")
    public Map<String, Object> regularizar(@PathVariable Long idHabitacion) {
        var hab = estanciaService.regularizarOcupadaSinRegistro(idHabitacion);
        return Map.of("mensaje", "Habitación " + hab.getNumero() + " liberada y enviada a limpieza (Mantenimiento).");
    }

    @GetMapping("/estancias/{id}")
    public Map<String, Object> folio(@PathVariable Long id) {
        Estancia e = estanciaService.buscar(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estancia", ApiMapper.estancia(e));
        if (e.isActiva()) {
            m.put("noches", estanciaService.nochesTranscurridas(e));
            m.put("importe", estanciaService.importeHospedaje(e));
        } else {
            m.put("noches", e.getNoches());
            m.put("importe", e.getPrecioNoche().multiply(BigDecimal.valueOf(e.getNoches())));
        }
        return m;
    }

    // ------------------------------------------------------------------ ESTADOS FÍSICOS

    @GetMapping("/estados")
    public List<Map<String, Object>> estados() {
        return habitacionService.listar().stream().map(ApiMapper::habitacion).toList();
    }

    @PostMapping("/estados/{id}/mantenimiento")
    public Map<String, Object> aMantenimiento(@PathVariable Long id) {
        var h = habitacionService.enviarAMantenimiento(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Habitación " + h.getNumero() + " de «" + h.getHotel().getNombre()
                + "» enviada a mantenimiento: ya no se ofrece a los huéspedes.");
        m.put("habitacion", ApiMapper.habitacion(h));
        return m;
    }

    @PostMapping("/estados/{id}/disponible")
    public Map<String, Object> aDisponible(@PathVariable Long id) {
        var h = habitacionService.marcarDisponible(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Habitación " + h.getNumero() + " de «" + h.getHotel().getNombre()
                + "» disponible nuevamente.");
        m.put("habitacion", ApiMapper.habitacion(h));
        return m;
    }

    // ------------------------------------------------------------------ RESERVACIÓN EN MOSTRADOR

    @GetMapping("/reservaciones/opciones")
    public Map<String, Object> opcionesReservacion() {
        return reservacionController.construirOpciones(null);
    }

    @PostMapping("/reservaciones")
    public ResponseEntity<Map<String, Object>> crearReservacionMostrador(@Valid @RequestBody ReservacionForm form) {
        Reservacion r = reservacionService.crear(form.getIdHabitacion(), form.getNombreHuesped(),
                form.getTelefono(), form.getCorreo(), form.getCiudadProcedencia(), form.getFechaEntrada(),
                form.getFechaSalida(), form.getAdultos(), form.getNinos(), "WEB");
        boolean llegaHoy = !r.getFechaEntrada().isAfter(LocalDate.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Reservación #" + r.getIdReservacion() + " creada para "
                + r.getNombreHuesped() + " (habitación " + r.getHabitacion().getNumero() + ")."
                + (llegaHoy ? " Confirma la llegada para entregar la habitación." : ""));
        m.put("reservacion", ApiMapper.reservacion(r));
        m.put("llegaHoy", llegaHoy);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }
}
