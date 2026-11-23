package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.dto.CheckOutForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.ReservacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reglas de negocio de las reservaciones. Las usan la IA (ReservationTool) y la pantalla /reservaciones.
 * Una reservación NO cambia el estado físico de la habitación (Disponible/Ocupada/Mantenimiento):
 * la disponibilidad por fechas se calcula con los traslapes entre reservaciones y estancias activas.
 */
@Service
public class ReservacionService {

    public static final int MAX_NOCHES = 30;
    private static final String RUTA = "/reservaciones";
    private static final Pattern CORREO = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final ReservacionRepository reservacionRepository;
    private final HabitacionRepository habitacionRepository;
    private final EstanciaRepository estanciaRepository;

    public ReservacionService(ReservacionRepository reservacionRepository,
                              HabitacionRepository habitacionRepository,
                              EstanciaRepository estanciaRepository) {
        this.reservacionRepository = reservacionRepository;
        this.habitacionRepository = habitacionRepository;
        this.estanciaRepository = estanciaRepository;
    }

    // ------------------------------------------------------------------ LISTADO PAGINADO Y CLASIFICADO
    public static final int TAM_PAGINA = 25;
    public static final List<String> VISTAS = List.of("proximas", "pasadas", "porpagar", "canceladas", "todas");

    private static int modo(String vista) {
        return switch (vista == null ? "" : vista) {
            case "proximas" -> 1; case "pasadas" -> 2; case "canceladas" -> 3; case "porpagar" -> 4; default -> 0;
        };
    }

    private static Long folio(String q) {
        try { return Long.parseLong(q.trim()); } catch (NumberFormatException e) { return -1L; }
    }

    private static String patron(String q) {
        return q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";
    }

    /** Una página de reservaciones de la vista pedida: próximas (más cercanas primero), pasadas, por pagar, canceladas o todas. */
    @Transactional(readOnly = true)
    public Page<Reservacion> pagina(String vista, int anio, String q, int pagina) {
        int md = modo(vista);
        Sort orden = md == 1 ? Sort.by("fechaEntrada", "idReservacion")
                : Sort.by(Sort.Order.desc("fechaEntrada"), Sort.Order.desc("idReservacion"));
        return reservacionRepository.paginaLista(md, EstadoReservacion.Cancelada,
                List.of(EstadoReservacion.Pendiente, EstadoReservacion.Pendiente_Pago), LocalDate.now(),
                Math.max(anio, 0), patron(q), folio(q == null ? "" : q), PageRequest.of(Math.max(pagina, 0), TAM_PAGINA, orden));
    }

    /** Cuántas reservaciones hay en cada vista (para los contadores de las pestañas). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> conteos(int anio, String q) {
        java.util.Map<String, Long> r = new java.util.LinkedHashMap<>();
        for (String v : VISTAS) {
            r.put(v, reservacionRepository.contarLista(modo(v), EstadoReservacion.Cancelada,
                    List.of(EstadoReservacion.Pendiente, EstadoReservacion.Pendiente_Pago), LocalDate.now(),
                    Math.max(anio, 0), patron(q), folio(q == null ? "" : q)));
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<Integer> aniosConReservaciones() {
        List<Integer> a = new ArrayList<>(reservacionRepository.aniosConReservaciones());
        a.sort(java.util.Comparator.reverseOrder());
        return a;
    }

    @Transactional(readOnly = true)
    public List<Reservacion> listar() {
        return reservacionRepository.findAllConDetalle();
    }

    @Transactional(readOnly = true)
    public List<Reservacion> listarDeUsuario(Long idUsuario) {
        return reservacionRepository.findByIdUsuarioConDetalle(idUsuario);
    }

    @Transactional(readOnly = true)
    public Reservacion buscar(Long id) {
        if (id == null) {
            throw new ReglaNegocioException("Indica el número de la reservación.");
        }
        return reservacionRepository.findByIdConDetalle(id).orElseThrow(
                () -> new RecursoNoEncontradoException("La reservación #" + id + " no existe.", RUTA));
    }

    /** Reservaciones en curso (ya iniciadas y sin check-out): las que recepción puede cerrar. */
    @Transactional(readOnly = true)
    public List<Reservacion> enCurso() {
        return reservacionRepository.findEnCurso(EstadoReservacion.Confirmada, LocalDate.now());
    }

    /** Llegadas esperadas hoy (y retrasadas) pendientes de check-in. */
    @Transactional(readOnly = true)
    public List<Reservacion> llegadasPendientes() {
        return reservacionRepository.findLlegadas(EstadoReservacion.Cancelada, LocalDate.now());
    }

    /** Busca reservaciones vigentes por folio (número), nombre del huésped o correo. */
    @Transactional(readOnly = true)
    public List<Reservacion> buscarParaCheckIn(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) {
            return List.of();
        }
        Long folio = -1L;
        String num = t.startsWith("#") ? t.substring(1).trim() : t;
        if (num.matches("\\d{1,18}")) {
            folio = Long.parseLong(num);
        }
        return reservacionRepository.buscarVigentes(EstadoReservacion.Cancelada, LocalDate.now(), folio,
                "%" + t.toLowerCase(java.util.Locale.ROOT) + "%");
    }

    @Transactional(readOnly = true)
    public boolean tieneCheckOut(Long idReservacion) {
        return estanciaRepository.existsByIdentificacion(claveFolio(idReservacion));
    }

    private static String claveFolio(Long idReservacion) {
        return "RES-" + idReservacion;
    }

    /**
     * Check-out directo de una reservación (ya no hay check-in aparte): cobra las noches reservadas más los cargos
     * extra, guarda el folio (Estancia cerrada) y manda la habitación a limpieza (Mantenimiento).
     */
    @Transactional
    public Estancia registrarCheckOut(Long idReservacion, CheckOutForm form) {
        Reservacion r = buscar(idReservacion);
        if (r.getEstado() != EstadoReservacion.Confirmada) {
            throw new ReglaNegocioException("Solo se puede hacer check-out de una reservación confirmada (la #"
                    + idReservacion + " está " + r.getEstado() + ").");
        }
        if (r.getFechaEntrada().isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La reservación #" + idReservacion + " empieza el " + r.getFechaEntrada()
                    + ": todavía no se puede hacer check-out.");
        }
        if (tieneCheckOut(idReservacion)) {
            throw new ReglaNegocioException("La reservación #" + idReservacion + " ya tiene check-out registrado.");
        }
        BigDecimal cargos = form.getCargosExtra() == null ? BigDecimal.ZERO : form.getCargosExtra();
        Habitacion hab = r.getHabitacion();

        Estancia e = new Estancia();
        e.setHabitacion(hab);
        e.setHuesped(r.getNombreHuesped());
        e.setIdentificacion(claveFolio(idReservacion));
        e.setNumHuespedes(r.getPersonas());
        e.setFechaEntrada(r.getFechaEntrada().atStartOfDay());
        e.setFechaSalidaPrevista(r.getFechaSalida());
        e.setFechaSalidaReal(LocalDateTime.now());
        e.setPrecioNoche(r.getPrecioNoche());
        e.setNoches(r.getNoches());
        e.setCargosExtra(cargos);
        e.setTotal(r.getCostoTotal().add(cargos));
        e.setMetodoPago(form.getMetodoPago());

        hab.setEstado(EstadoHabitacion.Mantenimiento);
        habitacionRepository.save(hab);
        return estanciaRepository.save(e);
    }

    private void exigirSinCheckOut(Reservacion r) {
        if (estanciaRepository.existsByIdentificacion(claveFolio(r.getIdReservacion()))) {
            throw new ReglaNegocioException("La reservación #" + r.getIdReservacion()
                    + " ya tiene check-out registrado y no se puede modificar ni cancelar.");
        }
    }

    /** Id de la habitación con ese número dentro de un hotel (para modificar la habitación de una reservación). */
    @Transactional(readOnly = true)
    public Optional<Long> idHabitacion(Long idHotel, String numero) {
        return habitacionRepository.findByHotelConDetalle(idHotel).stream()
                .filter(ha -> ha.getNumero().equalsIgnoreCase(numero.trim()))
                .map(Habitacion::getIdHabitacion)
                .findFirst();
    }

    /** Habitaciones libres en [entrada, salida) para esa cantidad de personas, de la más barata a la más cara. */
    @Transactional(readOnly = true)
    public List<Habitacion> habitacionesLibres(Long idHotel, Long idTipo, int personas,
                                               LocalDate entrada, LocalDate salida) {
        validarFechas(entrada, salida);
        List<Habitacion> libres = new ArrayList<>();
        for (Habitacion ha : habitacionRepository.findReservables(EstadoHabitacion.Mantenimiento)) {
            if (idHotel != null && !idHotel.equals(ha.getHotel().getIdHotel())) { continue; }
            if (idTipo != null && !idTipo.equals(ha.getTipo().getIdTipo())) { continue; }
            if (personas > ha.getTipo().getCapacidad()) { continue; }
            if (estaLibre(ha, entrada, salida, -1L)) { libres.add(ha); }
        }
        return libres;
    }

    @Transactional
    public Reservacion crear(Long idHabitacion, String nombre, String telefono, String correo, String ciudad,
                             LocalDate entrada, LocalDate salida, int adultos, int ninos, String origen) {
        return crear(idHabitacion, nombre, telefono, correo, ciudad, entrada, salida, adultos, ninos, origen, null);
    }

    /** idUsuario: cuenta dueña (null si la registra el personal). */
    @Transactional
    public Reservacion crear(Long idHabitacion, String nombre, String telefono, String correo, String ciudad,
                             LocalDate entrada, LocalDate salida, int adultos, int ninos, String origen, Long idUsuario) {
        return crear(idHabitacion, nombre, telefono, correo, ciudad, entrada, salida, adultos, ninos, origen, idUsuario, false);
    }

    /**
     * requierePago=true (huésped que reserva por chat/web): queda Pendiente_Pago y la habitación retenida
     * hasta que se paga o vencen los minutos de retención (PagoService cancela las vencidas).
     */
    @Transactional
    public Reservacion crear(Long idHabitacion, String nombre, String telefono, String correo, String ciudad,
                             LocalDate entrada, LocalDate salida, int adultos, int ninos, String origen, Long idUsuario,
                             boolean requierePago) {
        validarFechas(entrada, salida);
        Habitacion ha = habitacionReservable(idHabitacion);
        String tel = validarHuesped(nombre, telefono, correo);
        validarPersonas(ha, adultos, ninos);
        exigirLibre(ha, entrada, salida, -1L);

        Reservacion r = new Reservacion();
        r.setHabitacion(ha);
        r.setNombreHuesped(nombre.trim());
        r.setTelefono(tel);
        r.setCorreo(correo == null || correo.isBlank() ? null : correo.trim());
        r.setCiudadProcedencia(ciudad == null || ciudad.isBlank() ? null : ciudad.trim());
        r.setAdultos(adultos);
        r.setNinos(ninos);
        r.setOrigen(origen == null ? "WEB" : origen);
        r.setIdUsuario(idUsuario);
        r.setEstado(requierePago ? EstadoReservacion.Pendiente_Pago : EstadoReservacion.Confirmada);
        r.setFechaRegistro(java.time.LocalDateTime.now());
        aplicarFechasYCosto(r, entrada, salida);
        return reservacionRepository.save(r);
    }

    /** Los parámetros null no se modifican. */
    @Transactional
    public Reservacion modificar(Long id, Long idHabitacionNueva, String nombre, String telefono, String correo,
                                 String ciudad, LocalDate entrada, LocalDate salida, Integer adultos, Integer ninos) {
        Reservacion r = buscar(id);
        exigirSinCheckOut(r);
        if (r.getEstado() == EstadoReservacion.Cancelada) {
            throw new ReglaNegocioException("La reservación #" + id + " está cancelada y ya no se puede modificar. "
                    + "Crea una reservación nueva.");
        }
        LocalDate nuevaEntrada = entrada != null ? entrada : r.getFechaEntrada();
        LocalDate nuevaSalida = salida != null ? salida : r.getFechaSalida();
        validarFechas(nuevaEntrada, nuevaSalida, !nuevaEntrada.equals(r.getFechaEntrada()));

        Habitacion ha = idHabitacionNueva != null && !idHabitacionNueva.equals(r.getHabitacion().getIdHabitacion())
                ? habitacionReservable(idHabitacionNueva) : r.getHabitacion();
        int a = adultos != null ? adultos : r.getAdultos();
        int n = ninos != null ? ninos : r.getNinos();
        validarPersonas(ha, a, n);
        exigirLibre(ha, nuevaEntrada, nuevaSalida, r.getIdReservacion());

        String tel = telefono != null ? telefono : r.getTelefono();
        String nom = nombre != null ? nombre : r.getNombreHuesped();
        String cor = correo != null ? correo : r.getCorreo();
        r.setTelefono(validarHuesped(nom, tel, cor));
        r.setNombreHuesped(nom.trim());
        r.setCorreo(cor == null || cor.isBlank() ? null : cor.trim());
        if (ciudad != null) { r.setCiudadProcedencia(ciudad.isBlank() ? null : ciudad.trim()); }
        r.setHabitacion(ha);
        r.setAdultos(a);
        r.setNinos(n);
        aplicarFechasYCosto(r, nuevaEntrada, nuevaSalida);
        return reservacionRepository.save(r);
    }

    /** Cancela (queda el historial). */
    @Transactional
    public Reservacion cancelar(Long id) {
        Reservacion r = buscar(id);
        exigirSinCheckOut(r);
        if (r.getEstado() == EstadoReservacion.Cancelada) {
            throw new ReglaNegocioException("La reservación #" + id + " ya estaba cancelada.");
        }
        r.setEstado(EstadoReservacion.Cancelada);
        return reservacionRepository.save(r);
    }

    /** Borra el registro definitivamente. */
    @Transactional
    public Reservacion eliminar(Long id) {
        Reservacion r = buscar(id);
        reservacionRepository.delete(r);
        return r;
    }

    // ------------------------------------------------------------------ reglas

    private void validarFechas(LocalDate entrada, LocalDate salida) {
        validarFechas(entrada, salida, true);
    }

    /** exigirFuturo=false permite conservar una fecha de entrada que ya pasó (reservación en curso que se edita). */
    private void validarFechas(LocalDate entrada, LocalDate salida, boolean exigirFuturo) {
        if (entrada == null || salida == null) {
            throw new ReglaNegocioException("Se necesitan la fecha de entrada y la de salida.");
        }
        if (exigirFuturo && entrada.isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha de entrada no puede ser anterior a hoy (" + LocalDate.now() + ").");
        }
        if (!salida.isAfter(entrada)) {
            throw new ReglaNegocioException("La fecha de salida debe ser posterior a la de entrada.");
        }
        long noches = ChronoUnit.DAYS.between(entrada, salida);
        if (noches > MAX_NOCHES) {
            throw new ReglaNegocioException("La estancia máxima es de " + MAX_NOCHES + " noches (pediste " + noches + ").");
        }
    }

    public String validarHuesped(String nombre, String telefono, String correo) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del huésped es obligatorio.");
        }
        if (nombre.trim().length() > 120) {
            throw new ReglaNegocioException("El nombre del huésped no puede superar 120 caracteres.");
        }
        String tel = telefono == null ? "" : telefono.replaceAll("[\\s\\-().+]", "");
        if (!tel.matches("\\d{10}")) {
            throw new ReglaNegocioException("El teléfono debe tener 10 dígitos (por ejemplo 7221234567).");
        }
        if (correo != null && !correo.isBlank() && !CORREO.matcher(correo.trim()).matches()) {
            throw new ReglaNegocioException("El correo electrónico no tiene un formato válido.");
        }
        return tel;
    }

    private void validarPersonas(Habitacion ha, int adultos, int ninos) {
        if (adultos < 1) {
            throw new ReglaNegocioException("Debe haber al menos 1 adulto en la reservación.");
        }
        if (ninos < 0) {
            throw new ReglaNegocioException("El número de niños no puede ser negativo.");
        }
        int cap = ha.getTipo().getCapacidad();
        if (adultos + ninos > cap) {
            throw new ReglaNegocioException("La habitación " + ha.getNumero() + " (" + ha.getTipo().getNombre()
                    + ") admite máximo " + cap + " personas y la reservación es para " + (adultos + ninos) + ".");
        }
    }

    private Habitacion habitacionReservable(Long idHabitacion) {
        Habitacion ha = habitacionRepository.findById(idHabitacion).orElseThrow(
                () -> new ReglaNegocioException("La habitación indicada no existe."));
        if (!Boolean.TRUE.equals(ha.getHotel().getActivo())) {
            throw new ReglaNegocioException("El hotel «" + ha.getHotel().getNombre() + "» está inactivo y no admite reservaciones.");
        }
        if (ha.getEstado() == EstadoHabitacion.Mantenimiento) {
            throw new ReglaNegocioException("La habitación " + ha.getNumero() + " de «" + ha.getHotel().getNombre()
                    + "» está en mantenimiento y no se puede reservar.");
        }
        return ha;
    }

    private void exigirLibre(Habitacion ha, LocalDate entrada, LocalDate salida, Long excluirId) {
        if (!estaLibre(ha, entrada, salida, excluirId)) {
            throw new ReglaNegocioException("La habitación " + ha.getNumero() + " de «" + ha.getHotel().getNombre()
                    + "» no está libre del " + entrada + " al " + salida
                    + " (ya hay una reservación o un huésped hospedado en esas fechas).");
        }
    }

    private boolean estaLibre(Habitacion ha, LocalDate entrada, LocalDate salida, Long excluirId) {
        long traslapes = reservacionRepository.contarTraslapes(
                ha.getIdHabitacion(), entrada, salida, EstadoReservacion.Cancelada, excluirId);
        if (traslapes > 0) {
            return false;
        }
        Optional<Estancia> activa = estanciaRepository
                .findByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(ha.getIdHabitacion());
        // Un huésped hospedado ahora ocupa la habitación hasta su salida prevista.
        return activa.isEmpty() || !entrada.isBefore(activa.get().getFechaSalidaPrevista());
    }

    private void aplicarFechasYCosto(Reservacion r, LocalDate entrada, LocalDate salida) {
        long noches = ChronoUnit.DAYS.between(entrada, salida);
        BigDecimal precio = r.getHabitacion().getTipo().getPrecioBase();
        r.setFechaEntrada(entrada);
        r.setFechaSalida(salida);
        r.setNoches((int) noches);
        r.setPrecioNoche(precio);
        r.setCostoTotal(precio.multiply(BigDecimal.valueOf(noches)));
    }
}
