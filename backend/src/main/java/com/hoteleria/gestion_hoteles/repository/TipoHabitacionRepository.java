package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoHabitacionRepository extends JpaRepository<TipoHabitacion, Long> {

    List<TipoHabitacion> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdTipoNot(String nombre, Long idTipo);
}
