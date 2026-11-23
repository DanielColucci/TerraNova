package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.Estancia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EstanciaRepository extends JpaRepository<Estancia, Long> {

    String DETALLE = "select e from Estancia e join fetch e.habitacion ha join fetch ha.hotel h join fetch ha.tipo t left join fetch e.reservacion ";

    /** Estancias en curso (huéspedes hospedados), las que salen primero van arriba. */
    @Query(DETALLE + "where e.fechaSalidaReal is null order by e.fechaSalidaPrevista, h.nombre, ha.numero")
    List<Estancia> findActivasConDetalle();

    /** Últimos check-outs realizados. */
    @Query(DETALLE + "where e.fechaSalidaReal is not null order by e.fechaSalidaReal desc")
    List<Estancia> findFinalizadasRecientes(Pageable pageable);

    @Query(DETALLE + "where e.idEstancia = :id")
    Optional<Estancia> findByIdConDetalle(@Param("id") Long id);

    Optional<Estancia> findByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(Long idHabitacion);

    boolean existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(Long idHabitacion);

    long countByFechaSalidaRealIsNull();

    /** ¿Ya se hizo el check-out de esa reservación? (el folio se identifica como RES-<id>). */
    boolean existsByIdentificacion(String identificacion);

    Optional<Estancia> findByIdentificacion(String identificacion);

    /** Ingresos cobrados (check-outs) desde una fecha; 0 si no hay. */
    @Query("select coalesce(sum(e.total), 0) from Estancia e where e.fechaSalidaReal >= :desde")
    BigDecimal ingresosDesde(@Param("desde") LocalDateTime desde);
}
