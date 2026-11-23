package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findAllByOrderByNombreAsc();

    List<Hotel> findByActivoTrueOrderByNombreAsc();

    long countByActivoTrue();

    /** Ciudades con al menos un hotel activo (filtro de la búsqueda avanzada). */
    @Query("select distinct h.ciudad from Hotel h where h.activo = true order by h.ciudad")
    List<String> findCiudadesActivas();
}
