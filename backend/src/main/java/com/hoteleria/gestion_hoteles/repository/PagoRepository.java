package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.EstadoPago;
import com.hoteleria.gestion_hoteles.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findFirstByReservacion_IdReservacionAndEstado(Long idReservacion, EstadoPago estado);

    boolean existsByReservacion_IdReservacionAndEstado(Long idReservacion, EstadoPago estado);

    /** Folios de reservación con un pago aprobado (para mostrar «Pagado en línea» al administrador). */
    @Query("select p.reservacion.idReservacion from Pago p where p.estado = com.hoteleria.gestion_hoteles.entity.EstadoPago.Aprobado")
    List<Long> idsReservacionPagadas();
}
