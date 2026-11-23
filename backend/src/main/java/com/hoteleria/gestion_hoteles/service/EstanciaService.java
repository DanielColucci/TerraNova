package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.dto.CheckInForm;
import com.hoteleria.gestion_hoteles.dto.CheckOutForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reglas de recepción: Check-in (Historia 2) y Check-out (Historia 3). */
@Service
public class EstanciaService {

    private static final String RUTA = "/recepcion/checkout";

    private final EstanciaRepository estanciaRepository;
    private final HabitacionRepository habitacionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hoteleria.gestion_hoteles.repository.ReservacionRepository reservacionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PagoService pagoService;

    public EstanciaService(EstanciaRepository estanciaRepository, HabitacionRepository habitacionRepository) {
        this.estanciaRepository = estanciaRepository;
        this.habitacionRepository = habitacionRepository;
    }

    @Transactional(readOnly = true)
    public List<Habitacion> habitacionesParaCheckIn() {
        return habitacionRepository.findByEstadoEnHotelesActivos(EstadoHabitacion.Disponible);
    }

    @Transactional(readOnly = true)
    public List<Estancia> activas() {
        return estanciaRepository.findActivasConDetalle();
    }

    @Transactional(readOnly = true)
    public List<Estancia> recientes() {
        return estanciaRepository.findFinalizadasRecientes(PageRequest.of(0, 8));
    }

    @Transactional(readOnly = true)
    public List<Habitacion> ocupadasSinRegistro() {
        return habitacionRepository.findOcupadasSinEstancia(EstadoHabitacion.Ocupada);
    }

    @Transactional(readOnly = true)
    public Estancia buscar(Long id) {
        return estanciaRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La estancia solicitada no existe.", RUTA));
    }

    /** Noches a cobrar: las transcurridas desde el check-in hasta hoy (mínimo 1). */
    public int nochesTranscurridas(Estancia e) {
        long n = ChronoUnit.DAYS.between(e.getFechaEntrada().toLocalDate(), LocalDate.now());
        // Estancia que viene de una reservación: se cobran al menos las noches reservadas (si se queda más, las extra).
        long reservadas = e.getReservacion() != null && e.getReservacion().getNoches() != null ? e.getReservacion().getNoches() : 1;
        return (int) Math.max(Math.max(1, n), reservadas);
    }

    public BigDecimal importeHospedaje(Estancia e) {
        return e.getPrecioNoche().multiply(BigDecimal.valueOf(nochesTranscurridas(e)));
    }

    /** Historia 2: registra la llegada, asigna la habitación y la pasa a Ocupada. */
    @Transactional
    public Estancia registrarCheckIn(CheckInForm form) {
        Habitacion hab = habitacionRepository.findById(form.getIdHabitacion())
                .orElseThrow(() -> new ReglaNegocioException("idHabitacion", "La habitación seleccionada no existe."));

        if (!Boolean.TRUE.equals(hab.getHotel().getActivo())) {
            throw new ReglaNegocioException("idHabitacion", "El hotel «" + hab.getHotel().getNombre()
                    + "» está inactivo: no admite nuevos check-in.");
        }
        if (hab.getEstado() != EstadoHabitacion.Disponible) {
            throw new ReglaNegocioException("idHabitacion", "La habitación " + hab.getNumero()
                    + " no está disponible (estado actual: " + hab.getEstado() + ").");
        }
        if (estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(hab.getIdHabitacion())) {
            throw new ReglaNegocioException("idHabitacion",
                    "La habitación " + hab.getNumero() + " ya tiene un huésped registrado.");
        }
        int capacidad = hab.getTipo().getCapacidad();
        if (form.getNumHuespedes() > capacidad) {
            throw new ReglaNegocioException("numHuespedes", "La habitación " + hab.getNumero() + " es de tipo "
                    + hab.getTipo().getNombre() + " y admite máximo " + capacidad + " personas.");
        }

        Estancia e = new Estancia();
        e.setHabitacion(hab);
        e.setHuesped(form.getHuesped().trim());
        e.setIdentificacion(form.getIdentificacion().trim());
        e.setNumHuespedes(form.getNumHuespedes());
        e.setFechaEntrada(LocalDateTime.now());
        e.setFechaSalidaPrevista(form.getFechaSalidaPrevista());
        e.setPrecioNoche(hab.getTipo().getPrecioBase());

        hab.setEstado(EstadoHabitacion.Ocupada);
        habitacionRepository.save(hab);
        return estanciaRepository.save(e);
    }

    /**
     * Historia 3: liquida la estancia, cierra el folio y libera la habitación.
     * La habitación pasa a Mantenimiento (limpieza) hasta que recepción la marque Disponible.
     */
    @Transactional
    public Estancia registrarCheckOut(Long idEstancia, CheckOutForm form) {
        Estancia e = buscar(idEstancia);
        if (!e.isActiva()) {
            throw new ReglaNegocioException("La estancia de " + e.getHuesped() + " ya tiene check-out registrado.");
        }
        BigDecimal cargos = form.getCargosExtra() == null ? BigDecimal.ZERO : form.getCargosExtra();
        int noches = nochesTranscurridas(e);

        e.setFechaSalidaReal(LocalDateTime.now());
        e.setNoches(noches);
        e.setCargosExtra(cargos);
        e.setTotal(e.getPrecioNoche().multiply(BigDecimal.valueOf(noches)).add(cargos));
        e.setMetodoPago(form.getMetodoPago());

        Habitacion hab = e.getHabitacion();
        boolean aDisponible = "Disponible".equalsIgnoreCase(form.getEstadoHabitacion());
        hab.setEstado(aDisponible ? EstadoHabitacion.Disponible : EstadoHabitacion.Mantenimiento);
        habitacionRepository.save(hab);
        return estanciaRepository.save(e);
    }

    /**
     * CHECK-IN REAL: el huésped con reservación llegó al mostrador.
     * - Confirmada (pagada): crea la Estancia activa y deja la habitación Ocupada.
     * - Pendiente / Pendiente_Pago: cobra en mostrador (pago Aprobado), confirma la reservación y hace lo anterior,
     *   todo en UNA transacción (si algo falla, no queda nada a medias).
     */
    @Transactional
    public Estancia registrarCheckInReservacion(Long folio, com.hoteleria.gestion_hoteles.entity.MetodoPago metodoCobro,
                                                Long idUsuario) {
        com.hoteleria.gestion_hoteles.entity.Reservacion r = reservacionRepository.findByIdConDetalle(folio)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reservación #" + folio + " no existe.", "/recepcion/checkin"));
        com.hoteleria.gestion_hoteles.entity.EstadoReservacion est = r.getEstado();
        if (est == com.hoteleria.gestion_hoteles.entity.EstadoReservacion.Cancelada) {
            throw new ReglaNegocioException("La reservación #" + folio + " está cancelada: no se puede hacer check-in.");
        }
        if (r.getFechaEntrada().isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La reservación #" + folio + " empieza el " + r.getFechaEntrada()
                    + ": todavía no corresponde el check-in.");
        }
        if (!r.getFechaSalida().isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La reservación #" + folio + " ya terminó (salida " + r.getFechaSalida() + ").");
        }
        String clave = "RES-" + folio;
        if (estanciaRepository.existsByIdentificacion(clave)) {
            throw new ReglaNegocioException("La reservación #" + folio + " ya tiene check-in registrado.");
        }
        Habitacion hab = habitacionRepository.findById(r.getHabitacion().getIdHabitacion())
                .orElseThrow(() -> new ReglaNegocioException("La habitación asignada ya no existe."));
        if (hab.getEstado() != EstadoHabitacion.Disponible) {
            throw new ReglaNegocioException("La habitación " + hab.getNumero() + " no está disponible (estado: "
                    + hab.getEstado() + "). Libérala o resuelve el caso antes de entregarla.");
        }
        if (estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(hab.getIdHabitacion())) {
            throw new ReglaNegocioException("La habitación " + hab.getNumero() + " ya tiene un huésped registrado.");
        }
        // Escenario B: cobro en mostrador
        if (est != com.hoteleria.gestion_hoteles.entity.EstadoReservacion.Confirmada) {
            pagoService.cobrarEnMostrador(r, metodoCobro);
        }

        LocalDateTime ahora = LocalDateTime.now();
        Estancia e = new Estancia();
        e.setHabitacion(hab);
        e.setReservacion(r);
        e.setIdUsuarioCheckin(idUsuario);
        e.setHuesped(r.getNombreHuesped());
        e.setIdentificacion(clave);
        e.setNumHuespedes(Math.max(1, (r.getAdultos() == null ? 1 : r.getAdultos()) + (r.getNinos() == null ? 0 : r.getNinos())));
        e.setFechaEntrada(ahora);
        e.setFechaSalidaPrevista(r.getFechaSalida());
        e.setPrecioNoche(r.getPrecioNoche());
        r.setFechaCheckin(ahora);
        reservacionRepository.save(r);
        hab.setEstado(EstadoHabitacion.Ocupada);
        habitacionRepository.save(hab);
        return estanciaRepository.save(e);
    }

    /**
     * Regulariza una habitación marcada Ocupada que no tiene huésped registrado (datos capturados antes de existir
     * el check-in): se libera hacia Mantenimiento (limpieza), igual que un check-out.
     */
    @Transactional
    public Habitacion regularizarOcupadaSinRegistro(Long idHabitacion) {
        Habitacion hab = habitacionRepository.findById(idHabitacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("La habitación solicitada no existe.", RUTA));
        if (hab.getEstado() != EstadoHabitacion.Ocupada
                || estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(idHabitacion)) {
            throw new ReglaNegocioException("Esa habitación no requiere regularización.");
        }
        hab.setEstado(EstadoHabitacion.Mantenimiento);
        return habitacionRepository.save(hab);
    }
}
