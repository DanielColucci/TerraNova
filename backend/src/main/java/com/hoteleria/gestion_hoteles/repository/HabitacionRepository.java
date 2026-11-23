package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HabitacionRepository extends JpaRepository<Habitacion, Long> {

    /** Todas las habitaciones con su hotel y tipo cargados (INNER JOIN FETCH), ordenadas por hotel y número. */
    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t order by h.nombre, ha.numero")
    List<Habitacion> findAllConDetalle();

    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t "
            + "where h.idHotel = :idHotel order by ha.numero")
    List<Habitacion> findByHotelConDetalle(@Param("idHotel") Long idHotel);

    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t "
            + "where t.idTipo = :idTipo order by h.nombre, ha.numero")
    List<Habitacion> findByTipoConDetalle(@Param("idTipo") Long idTipo);

    /** Habitaciones de hoteles ACTIVOS en un estado dado (para check-in: Disponible). */
    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t "
            + "where ha.estado = :estado and h.activo = true order by h.nombre, ha.numero")
    List<Habitacion> findByEstadoEnHotelesActivos(@Param("estado") EstadoHabitacion estado);

    /** Habitaciones marcadas Ocupada que no tienen una estancia activa registrada (datos heredados). */
    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t "
            + "where ha.estado = :estado and not exists "
            + "(select e.idEstancia from Estancia e where e.habitacion = ha and e.fechaSalidaReal is null) "
            + "order by h.nombre, ha.numero")
    List<Habitacion> findOcupadasSinEstancia(@Param("estado") EstadoHabitacion estado);

    /** Habitaciones de hoteles activos que no están en el estado indicado (se usa para excluir Mantenimiento al reservar). */
    @Query("select ha from Habitacion ha join fetch ha.hotel h join fetch ha.tipo t "
            + "where h.activo = true and ha.estado <> :excluido order by t.precioBase, h.nombre, ha.numero")
    List<Habitacion> findReservables(@Param("excluido") EstadoHabitacion excluido);

    boolean existsByHotel_IdHotel(Long idHotel);

    long countByHotel_IdHotel(Long idHotel);

    boolean existsByTipo_IdTipo(Long idTipo);

    long countByTipo_IdTipo(Long idTipo);

    boolean existsByHotel_IdHotelAndNumeroIgnoreCase(Long idHotel, String numero);

    boolean existsByHotel_IdHotelAndNumeroIgnoreCaseAndIdHabitacionNot(Long idHotel, String numero, Long idHabitacion);

    long countByEstado(EstadoHabitacion estado);

    long countByEstadoAndHotel_ActivoTrue(EstadoHabitacion estado);
}
