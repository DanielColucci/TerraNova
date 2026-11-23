package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TipoHabitacionService {

    private static final String RUTA = "/tipos";

    private final TipoHabitacionRepository tipoRepository;
    private final HabitacionRepository habitacionRepository;

    public TipoHabitacionService(TipoHabitacionRepository tipoRepository, HabitacionRepository habitacionRepository) {
        this.tipoRepository = tipoRepository;
        this.habitacionRepository = habitacionRepository;
    }

    @Transactional(readOnly = true)
    public List<TipoHabitacion> listar() {
        List<TipoHabitacion> tipos = tipoRepository.findAllByOrderByNombreAsc();
        tipos.forEach(t -> t.getHabitaciones().size());
        return tipos;
    }

    @Transactional(readOnly = true)
    public TipoHabitacion buscar(Long id) {
        return tipoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El tipo de habitación solicitado no existe.", RUTA));
    }

    @Transactional
    public TipoHabitacion crear(TipoHabitacion tipo) {
        tipo.setIdTipo(null);
        tipo.setNombre(tipo.getNombre() == null ? null : tipo.getNombre().trim());
        tipo.setDescripcion(tipo.getDescripcion() == null ? null : tipo.getDescripcion().trim());
        if (tipoRepository.existsByNombreIgnoreCase(tipo.getNombre())) {
            throw new ReglaNegocioException("nombre", "Ya existe un tipo de habitación con ese nombre.");
        }
        return guardarSeguro(tipo);
    }

    @Transactional
    public TipoHabitacion actualizar(Long id, TipoHabitacion datos) {
        TipoHabitacion existente = buscar(id);
        String nombre = datos.getNombre() == null ? null : datos.getNombre().trim();
        if (tipoRepository.existsByNombreIgnoreCaseAndIdTipoNot(nombre, id)) {
            throw new ReglaNegocioException("nombre", "Ya existe otro tipo de habitación con ese nombre.");
        }
        existente.setNombre(nombre);
        existente.setCapacidad(datos.getCapacidad());
        existente.setDescripcion(datos.getDescripcion() == null ? null : datos.getDescripcion().trim());
        existente.setPrecioBase(datos.getPrecioBase());
        return guardarSeguro(existente);
    }

    /** Regla de negocio: un tipo usado por alguna habitación no puede eliminarse. */
    @Transactional
    public void eliminar(Long id) {
        TipoHabitacion tipo = buscar(id);
        long total = habitacionRepository.countByTipo_IdTipo(id);
        if (total > 0) {
            throw new ReglaNegocioException("No se puede eliminar el tipo «" + tipo.getNombre()
                    + "» porque está siendo utilizado por " + total
                    + (total == 1 ? " habitación." : " habitaciones.")
                    + " Reasigna o elimina esas habitaciones primero.");
        }
        try {
            tipoRepository.delete(tipo);
            tipoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se puede eliminar el tipo «" + tipo.getNombre()
                    + "» porque está siendo utilizado.");
        }
    }

    private TipoHabitacion guardarSeguro(TipoHabitacion tipo) {
        try {
            return tipoRepository.saveAndFlush(tipo);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("No se pudo guardar el tipo de habitación: revisa que el nombre no esté repetido y que los datos sean válidos.");
        }
    }
}
