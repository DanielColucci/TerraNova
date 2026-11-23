package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HotelService {

    private static final String RUTA = "/hoteles";

    private final HotelRepository hotelRepository;
    private final HabitacionRepository habitacionRepository;

    public HotelService(HotelRepository hotelRepository, HabitacionRepository habitacionRepository) {
        this.hotelRepository = hotelRepository;
        this.habitacionRepository = habitacionRepository;
    }

    @Transactional(readOnly = true)
    public List<Hotel> listar() {
        List<Hotel> hoteles = hotelRepository.findAllByOrderByNombreAsc();
        // Inicializa la colección para poder mostrar el total de habitaciones en la tabla
        hoteles.forEach(h -> h.getHabitaciones().size());
        return hoteles;
    }

    @Transactional(readOnly = true)
    public List<Hotel> listarActivos() {
        return hotelRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Hotel buscar(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El hotel solicitado no existe.", RUTA));
    }

    @Transactional
    public Hotel crear(Hotel hotel) {
        hotel.setIdHotel(null);
        normalizar(hotel);
        return guardarSeguro(hotel);
    }

    @Transactional
    public Hotel actualizar(Long id, Hotel datos) {
        Hotel existente = buscar(id);
        normalizar(datos);
        existente.setNombre(datos.getNombre());
        existente.setDireccion(datos.getDireccion());
        existente.setCiudad(datos.getCiudad());
        existente.setTelefono(datos.getTelefono());
        existente.setCorreo(datos.getCorreo());
        existente.setActivo(datos.getActivo());
        return guardarSeguro(existente);
    }

    /**
     * Regla de negocio: no se elimina un hotel con habitaciones asociadas;
     * en su lugar se sugiere marcarlo como inactivo.
     */
    @Transactional
    public void eliminar(Long id) {
        Hotel hotel = buscar(id);
        long total = habitacionRepository.countByHotel_IdHotel(id);
        if (total > 0) {
            throw new ReglaNegocioException("No se puede eliminar el hotel «" + hotel.getNombre()
                    + "» porque tiene " + total + (total == 1 ? " habitación asociada" : " habitaciones asociadas")
                    + ". Te sugerimos marcarlo como Inactivo en lugar de eliminarlo.");
        }
        try {
            hotelRepository.delete(hotel);
            hotelRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar el hotel «" + hotel.getNombre()
                    + "» porque tiene información asociada. Márcalo como Inactivo.");
        }
    }

    private void normalizar(Hotel h) {
        h.setNombre(limpiar(h.getNombre()));
        h.setDireccion(limpiar(h.getDireccion()));
        h.setCiudad(limpiar(h.getCiudad()));
        h.setTelefono(limpiar(h.getTelefono()));
        h.setCorreo(limpiar(h.getCorreo()));
        if (h.getActivo() == null) {
            h.setActivo(Boolean.TRUE);
        }
    }

    private String limpiar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private Hotel guardarSeguro(Hotel hotel) {
        try {
            return hotelRepository.saveAndFlush(hotel);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se pudo guardar el hotel: los datos no cumplen las restricciones de la base de datos.");
        }
    }
}
