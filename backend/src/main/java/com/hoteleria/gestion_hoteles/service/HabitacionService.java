package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.dto.DisponiblesPorHotel;
import com.hoteleria.gestion_hoteles.dto.HabitacionForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HabitacionService {

    private static final String RUTA = "/habitaciones";

    private final HabitacionRepository habitacionRepository;
    private final HotelRepository hotelRepository;
    private final TipoHabitacionRepository tipoRepository;

    public HabitacionService(HabitacionRepository habitacionRepository,
                             HotelRepository hotelRepository,
                             TipoHabitacionRepository tipoRepository) {
        this.habitacionRepository = habitacionRepository;
        this.hotelRepository = hotelRepository;
        this.tipoRepository = tipoRepository;
    }

    @Transactional(readOnly = true)
    public List<Habitacion> listar() {
        return habitacionRepository.findAllConDetalle();
    }

    @Transactional(readOnly = true)
    public List<Habitacion> listarPorHotel(Long idHotel) {
        return habitacionRepository.findByHotelConDetalle(idHotel);
    }

    @Transactional(readOnly = true)
    public List<Habitacion> listarPorTipo(Long idTipo) {
        return habitacionRepository.findByTipoConDetalle(idTipo);
    }

    /** Habitaciones Disponibles por hotel activo, ordenado por nombre de hotel (panel principal). */
    @Transactional(readOnly = true)
    public List<DisponiblesPorHotel> disponiblesPorHotel() {
        Map<String, Long> conteo = new LinkedHashMap<>();
        habitacionRepository.findAllConDetalle().stream()
                .filter(h -> h.getEstado() == EstadoHabitacion.Disponible)
                .filter(h -> Boolean.TRUE.equals(h.getHotel().getActivo()))
                .forEach(h -> conteo.merge(h.getHotel().getNombre(), 1L, Long::sum));
        return conteo.entrySet().stream()
                .map(e -> new DisponiblesPorHotel(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DisponiblesPorHotel::hotel))
                .toList();
    }

    @Transactional(readOnly = true)
    public Habitacion buscar(Long id) {
        return habitacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La habitación solicitada no existe.", RUTA));
    }

    @Transactional(readOnly = true)
    public HabitacionForm obtenerFormulario(Long id) {
        Habitacion h = buscar(id);
        HabitacionForm form = new HabitacionForm();
        form.setIdHabitacion(h.getIdHabitacion());
        form.setIdHotel(h.getHotel().getIdHotel());
        form.setIdTipo(h.getTipo().getIdTipo());
        form.setNumero(h.getNumero());
        form.setPiso(h.getPiso());
        form.setEstado(h.getEstado());
        return form;
    }

    @Transactional
    public Habitacion crear(HabitacionForm form) {
        Hotel hotel = obtenerHotel(form.getIdHotel());
        TipoHabitacion tipo = obtenerTipo(form.getIdTipo());
        if (!Boolean.TRUE.equals(hotel.getActivo())) {
            throw new ReglaNegocioException("idHotel",
                    "El hotel «" + hotel.getNombre() + "» está inactivo y no puede recibir nuevas habitaciones.");
        }
        String numero = form.getNumero().trim();
        if (habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCase(hotel.getIdHotel(), numero)) {
            throw duplicada(hotel, numero);
        }
        Habitacion h = new Habitacion();
        h.setHotel(hotel);
        h.setTipo(tipo);
        h.setNumero(numero);
        h.setPiso(form.getPiso());
        h.setEstado(form.getEstado());
        return guardarSeguro(h, hotel, numero);
    }

    @Transactional
    public Habitacion actualizar(Long id, HabitacionForm form) {
        Habitacion h = buscar(id);
        Hotel hotel = obtenerHotel(form.getIdHotel());
        TipoHabitacion tipo = obtenerTipo(form.getIdTipo());
        boolean cambioDeHotel = !hotel.getIdHotel().equals(h.getHotel().getIdHotel());
        if (cambioDeHotel && !Boolean.TRUE.equals(hotel.getActivo())) {
            throw new ReglaNegocioException("idHotel",
                    "El hotel «" + hotel.getNombre() + "» está inactivo y no puede recibir habitaciones.");
        }
        String numero = form.getNumero().trim();
        if (habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCaseAndIdHabitacionNot(
                hotel.getIdHotel(), numero, id)) {
            throw duplicada(hotel, numero);
        }
        h.setHotel(hotel);
        h.setTipo(tipo);
        h.setNumero(numero);
        h.setPiso(form.getPiso());
        h.setEstado(form.getEstado());
        return guardarSeguro(h, hotel, numero);
    }

    /** Intentar eliminar una habitación inexistente se informa con un mensaje claro. */
    @Transactional
    public void eliminar(Long id) {
        if (!habitacionRepository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No se pudo eliminar: la habitación no existe (quizá ya fue eliminada).", RUTA);
        }
        try {
            habitacionRepository.deleteById(id);
            habitacionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar la habitación porque tiene información asociada.");
        }
    }

    /** Historia 3: Disponible -> Mantenimiento (avería, limpieza profunda, reparación). */
    @Transactional
    public Habitacion enviarAMantenimiento(Long id) {
        Habitacion h = buscar(id);
        validarOperable(h);
        if (h.getEstado() == EstadoHabitacion.Ocupada) {
            throw new ReglaNegocioException("La habitación " + h.getNumero() + " de «" + h.getHotel().getNombre()
                    + "» está ocupada: primero registra el check-out del huésped.");
        }
        if (h.getEstado() == EstadoHabitacion.Mantenimiento) {
            throw new ReglaNegocioException("La habitación " + h.getNumero() + " ya está en mantenimiento.");
        }
        h.setEstado(EstadoHabitacion.Mantenimiento);
        return habitacionRepository.save(h);
    }

    /** Historia 3: Mantenimiento -> Disponible (la habitación vuelve a ofrecerse). */
    @Transactional
    public Habitacion marcarDisponible(Long id) {
        Habitacion h = buscar(id);
        validarOperable(h);
        if (h.getEstado() != EstadoHabitacion.Mantenimiento) {
            throw new ReglaNegocioException("Solo una habitación en mantenimiento puede regresar a Disponible "
                    + "(estado actual de la " + h.getNumero() + ": " + h.getEstado() + ").");
        }
        h.setEstado(EstadoHabitacion.Disponible);
        return habitacionRepository.save(h);
    }

    private void validarOperable(Habitacion h) {
        if (!Boolean.TRUE.equals(h.getHotel().getActivo())) {
            throw new ReglaNegocioException("El hotel «" + h.getHotel().getNombre()
                    + "» está inactivo: no se pueden cambiar estados operativos de sus habitaciones.");
        }
    }

    private Hotel obtenerHotel(Long idHotel) {
        return hotelRepository.findById(idHotel)
                .orElseThrow(() -> new ReglaNegocioException("idHotel", "El hotel seleccionado no existe."));
    }

    private TipoHabitacion obtenerTipo(Long idTipo) {
        return tipoRepository.findById(idTipo)
                .orElseThrow(() -> new ReglaNegocioException("idTipo", "El tipo de habitación seleccionado no existe."));
    }

    private ReglaNegocioException duplicada(Hotel hotel, String numero) {
        return new ReglaNegocioException("numero",
                "La habitación " + numero + " ya existe en «" + hotel.getNombre()
                        + "». El número puede repetirse en otros hoteles, pero no dentro del mismo.");
    }

    private Habitacion guardarSeguro(Habitacion h, Hotel hotel, String numero) {
        try {
            return habitacionRepository.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            // Respaldo: la restricción única de la BD (id_hotel + numero) también protege la regla
            throw duplicada(hotel, numero);
        }
    }
}
