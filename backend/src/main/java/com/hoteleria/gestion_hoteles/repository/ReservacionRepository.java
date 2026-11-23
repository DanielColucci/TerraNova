package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservacionRepository extends JpaRepository<Reservacion, Long> {

    String DETALLE = "select r from Reservacion r join fetch r.habitacion ha join fetch ha.hotel h join fetch ha.tipo t ";

    @Query(DETALLE + "order by r.fechaEntrada desc, r.idReservacion desc")
    List<Reservacion> findAllConDetalle();

    @Query(DETALLE + "where r.idUsuario = :idUsuario order by r.fechaEntrada desc, r.idReservacion desc")
    List<Reservacion> findByIdUsuarioConDetalle(@Param("idUsuario") Long idUsuario);

    /**
     * Reservaciones EN CURSO: confirmadas, ya iniciadas (entrada hoy o antes) y sin check-out.
     * El check-out de una reservación guarda su folio (Estancia) con identificacion = 'RES-<id>'.
     */
    @Query(DETALLE + "where r.estado = :estado and r.fechaEntrada <= :hoy "
            + "and not exists (select 1 from Estancia e where e.identificacion = concat('RES-', cast(r.idReservacion as string))) "
            + "order by r.fechaSalida, r.idReservacion")
    List<Reservacion> findEnCurso(@Param("estado") EstadoReservacion estado, @Param("hoy") LocalDate hoy);

    String SIN_ESTANCIA = "and not exists (select 1 from Estancia e where e.identificacion = concat('RES-', cast(r.idReservacion as string))) ";

    /** Llegadas esperadas: reservaciones vigentes (no canceladas) que ya debieron llegar (hoy o antes) y aún no hacen check-in. */
    @Query(DETALLE + "where r.estado <> :cancelada and r.fechaEntrada <= :hoy and r.fechaSalida > :hoy " + SIN_ESTANCIA
            + "order by r.fechaEntrada, r.idReservacion")
    List<Reservacion> findLlegadas(@Param("cancelada") EstadoReservacion cancelada, @Param("hoy") LocalDate hoy);

    /** Búsqueda en mostrador: folio, nombre o correo (q ya viene en minúsculas con %...%). */
    @Query(DETALLE + "where r.estado <> :cancelada and r.fechaSalida > :hoy " + SIN_ESTANCIA
            + "and (r.idReservacion = :folio or lower(r.nombreHuesped) like :q or lower(coalesce(r.correo, '')) like :q) "
            + "order by r.fechaEntrada, r.idReservacion")
    List<Reservacion> buscarVigentes(@Param("cancelada") EstadoReservacion cancelada, @Param("hoy") LocalDate hoy,
                                     @Param("folio") Long folio, @Param("q") String q);

    List<Reservacion> findByEstadoAndFechaRegistroBefore(EstadoReservacion estado, java.time.LocalDateTime limite);

    @Query(DETALLE + "where r.idReservacion = :id")
    Optional<Reservacion> findByIdConDetalle(@Param("id") Long id);

    /**
     * Cuántas reservaciones NO canceladas de esa habitación se traslapan con [entrada, salida).
     * Traslape: la existente entra antes de que salga la nueva y sale después de que entra la nueva
     * (el día de salida de una puede ser el día de entrada de otra).
     * excluirId: la reservación que se está modificando (usa -1 al crear).
     */
    @Query("select count(r) from Reservacion r where r.habitacion.idHabitacion = :idHabitacion "
            + "and r.estado <> :cancelada and r.fechaEntrada < :salida and r.fechaSalida > :entrada "
            + "and r.idReservacion <> :excluirId")
    long contarTraslapes(@Param("idHabitacion") Long idHabitacion,
                         @Param("entrada") LocalDate entrada,
                         @Param("salida") LocalDate salida,
                         @Param("cancelada") EstadoReservacion cancelada,
                         @Param("excluirId") Long excluirId);

    // ---------- Listado paginado y clasificado (administrador / recepción) ----------
    /** modo: 0 todas · 1 próximas y en curso · 2 pasadas · 3 canceladas · 4 por pagar. anio 0 = todos. q '' = sin búsqueda. */
    String FILTRO_LISTA = "(:modo = 0 "
            + "or (:modo = 1 and r.estado <> :canc and r.fechaSalida >= :hoy) "
            + "or (:modo = 2 and r.estado <> :canc and r.fechaSalida < :hoy) "
            + "or (:modo = 3 and r.estado = :canc) "
            + "or (:modo = 4 and r.estado in (:porPagar))) "
            + "and (:anio = 0 or year(r.fechaEntrada) = :anio) "
            + "and (:q = '' or r.idReservacion = :folio or lower(r.nombreHuesped) like :q or lower(coalesce(r.correo, '')) like :q)";

    @Query(value = DETALLE + "where " + FILTRO_LISTA,
           countQuery = "select count(r) from Reservacion r where " + FILTRO_LISTA)
    Page<Reservacion> paginaLista(@Param("modo") int modo, @Param("canc") EstadoReservacion canc,
                                  @Param("porPagar") java.util.Collection<EstadoReservacion> porPagar,
                                  @Param("hoy") LocalDate hoy, @Param("anio") int anio,
                                  @Param("q") String q, @Param("folio") Long folio, Pageable pageable);

    @Query("select count(r) from Reservacion r where " + FILTRO_LISTA)
    long contarLista(@Param("modo") int modo, @Param("canc") EstadoReservacion canc,
                     @Param("porPagar") java.util.Collection<EstadoReservacion> porPagar,
                     @Param("hoy") LocalDate hoy, @Param("anio") int anio,
                     @Param("q") String q, @Param("folio") Long folio);

    @Query("select distinct year(r.fechaEntrada) from Reservacion r")
    List<Integer> aniosConReservaciones();
}
