package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.dto.BusquedaView;
import com.hoteleria.gestion_hoteles.dto.EjecutivoHotelView;
import com.hoteleria.gestion_hoteles.dto.EjecutivoTipoView;
import com.hoteleria.gestion_hoteles.dto.GerenteViews;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas SQL nativas de las historias de usuario (Historia 1: búsqueda y Historia 5: dashboard ejecutivo).
 * Los filtros opcionales usan 0 / '' / -1 como "sin filtro" (el servicio convierte null en esos valores).
 */
public interface ConsultaRepository extends Repository<Habitacion, Long> {

    /**
     * Historia 1: búsqueda avanzada de disponibilidad.
     * Filtros opcionales: ciudad ('' = todas), hotel (0), tipo (0), capacidad mínima (0) y precio máximo (negativo = sin tope).
     */
    @Query(value = """
            SELECT
                ha.id_habitacion AS idhabitacion,
                h.nombre         AS hotel,
                h.ciudad         AS ciudad,
                ha.numero        AS habitacion,
                ha.piso          AS piso,
                t.nombre         AS tipo,
                t.descripcion    AS descripcion,
                t.capacidad      AS capacidad,
                t.precio_base    AS precio
            FROM habitacion ha
            INNER JOIN hotel h
                ON ha.id_hotel = h.id_hotel
            INNER JOIN tipo_habitacion t
                ON ha.id_tipo = t.id_tipo
            WHERE ha.estado = 'Disponible'
              AND h.activo = TRUE
              AND (:ciudad = '' OR h.ciudad = :ciudad)
              AND (:idHotel = 0 OR h.id_hotel = :idHotel)
              AND (:idTipo = 0 OR t.id_tipo = :idTipo)
              AND (:capacidad = 0 OR t.capacidad >= :capacidad)
              AND (:precioMax < 0 OR t.precio_base <= :precioMax)
            ORDER BY t.precio_base, h.nombre, ha.numero
            """, nativeQuery = true)
    List<BusquedaView> busquedaDisponibilidad(@Param("ciudad") String ciudad,
                                              @Param("idHotel") long idHotel,
                                              @Param("idTipo") long idTipo,
                                              @Param("capacidad") int capacidad,
                                              @Param("precioMax") BigDecimal precioMax);

    /** Historia 5: ocupación, ingreso en curso y costo de oportunidad por hotel (JOIN + GROUP BY + SUM/CASE). */
    @Query(value = """
            SELECT
                h.nombre AS hotel,
                h.ciudad AS ciudad,
                CASE WHEN h.activo = TRUE THEN 1 ELSE 0 END AS activo,
                COUNT(ha.id_habitacion) AS total,
                COALESCE(SUM(CASE WHEN ha.estado = 'Disponible'    THEN 1 ELSE 0 END), 0) AS disponibles,
                COALESCE(SUM(CASE WHEN ha.estado = 'Ocupada'       THEN 1 ELSE 0 END), 0) AS ocupadas,
                COALESCE(SUM(CASE WHEN ha.estado = 'Mantenimiento' THEN 1 ELSE 0 END), 0) AS mantenimiento,
                COALESCE(SUM(CASE WHEN ha.estado = 'Ocupada'       THEN t.precio_base ELSE 0 END), 0) AS ingreso,
                COALESCE(SUM(CASE WHEN ha.estado = 'Mantenimiento' THEN t.precio_base ELSE 0 END), 0) AS costo,
                COALESCE(SUM(t.precio_base), 0) AS potencial
            FROM hotel h
            LEFT JOIN habitacion ha
                ON ha.id_hotel = h.id_hotel
            LEFT JOIN tipo_habitacion t
                ON ha.id_tipo = t.id_tipo
            GROUP BY h.id_hotel, h.nombre, h.ciudad, h.activo
            ORDER BY h.nombre
            """, nativeQuery = true)
    List<EjecutivoHotelView> ejecutivoPorHotel();

    /** Historia 5: ocupación por tipo de habitación (solo hoteles activos). */
    @Query(value = """
            SELECT
                t.nombre      AS tipo,
                t.capacidad   AS capacidad,
                t.precio_base AS precio,
                COUNT(ha.id_habitacion) AS total,
                COALESCE(SUM(CASE WHEN ha.estado = 'Ocupada'       THEN 1 ELSE 0 END), 0) AS ocupadas,
                COALESCE(SUM(CASE WHEN ha.estado = 'Mantenimiento' THEN 1 ELSE 0 END), 0) AS mantenimiento
            FROM tipo_habitacion t
            LEFT JOIN habitacion ha
                ON ha.id_tipo = t.id_tipo
               AND ha.id_hotel IN (SELECT hx.id_hotel FROM hotel hx WHERE hx.activo = TRUE)
            GROUP BY t.id_tipo, t.nombre, t.capacidad, t.precio_base
            ORDER BY t.nombre
            """, nativeQuery = true)
    List<EjecutivoTipoView> ejecutivoPorTipo();

    // ====================== Fase 4: panel y informes del GERENTE ======================
    // :desdeTs / :hastaTs = [inicio del primer día, inicio del día siguiente al último) para pagos y registros;
    // :desde / :hastaExcl = mismas fechas como LocalDate para noches de reservación. :idHotel = 0 -> todas las sedes.

    /** Por hotel: ingresos aprobados del periodo, noches vendidas (traslape de reservaciones no canceladas) y ocupación actual. */
    @Query(value = """
            SELECT
                h.id_hotel AS idhotel,
                h.nombre   AS hotel,
                h.ciudad   AS ciudad,
                (SELECT COUNT(*) FROM habitacion x WHERE x.id_hotel = h.id_hotel) AS habitaciones,
                (SELECT COUNT(*) FROM habitacion x WHERE x.id_hotel = h.id_hotel AND x.estado = 'Ocupada') AS ocupadasahora,
                (SELECT COALESCE(SUM(GREATEST(0, DATEDIFF(LEAST(r.fecha_salida, :hastaExcl), GREATEST(r.fecha_entrada, :desde)))), 0)
                   FROM reservacion r
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_hotel = h.id_hotel
                    AND r.estado <> 'Cancelada'
                    AND r.fecha_entrada < :hastaExcl AND r.fecha_salida > :desde) AS nochesvendidas,
                (SELECT COALESCE(SUM(p.monto), 0)
                   FROM pago p
                   JOIN reservacion r ON r.id_reservacion = p.id_reservacion
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_hotel = h.id_hotel AND p.estado = 'Aprobado'
                    AND p.fecha >= :desdeTs AND p.fecha < :hastaTs) AS ingresos,
                (SELECT COUNT(*)
                   FROM pago p
                   JOIN reservacion r ON r.id_reservacion = p.id_reservacion
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_hotel = h.id_hotel AND p.estado = 'Aprobado'
                    AND p.fecha >= :desdeTs AND p.fecha < :hastaTs) AS pagos
            FROM hotel h
            WHERE (:idHotel = 0 OR h.id_hotel = :idHotel)
            ORDER BY h.nombre
            """, nativeQuery = true)
    List<GerenteViews.HotelView> gerentePorHotel(@Param("desde") LocalDate desde,
                                                 @Param("hastaExcl") LocalDate hastaExcl,
                                                 @Param("desdeTs") LocalDateTime desdeTs,
                                                 @Param("hastaTs") LocalDateTime hastaTs,
                                                 @Param("idHotel") long idHotel);

    /** Por tipo de habitación: ingresos aprobados, pagos, reservaciones que inician en el periodo y promedio de noches. */
    @Query(value = """
            SELECT
                t.id_tipo AS idtipo,
                t.nombre  AS tipo,
                (SELECT COALESCE(SUM(p.monto), 0)
                   FROM pago p
                   JOIN reservacion r ON r.id_reservacion = p.id_reservacion
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_tipo = t.id_tipo AND p.estado = 'Aprobado'
                    AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
                    AND (:idHotel = 0 OR x.id_hotel = :idHotel)) AS ingresos,
                (SELECT COUNT(*)
                   FROM pago p
                   JOIN reservacion r ON r.id_reservacion = p.id_reservacion
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_tipo = t.id_tipo AND p.estado = 'Aprobado'
                    AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
                    AND (:idHotel = 0 OR x.id_hotel = :idHotel)) AS pagos,
                (SELECT COUNT(*)
                   FROM reservacion r
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_tipo = t.id_tipo AND r.estado <> 'Cancelada'
                    AND r.fecha_entrada >= :desde AND r.fecha_entrada < :hastaExcl
                    AND (:idHotel = 0 OR x.id_hotel = :idHotel)) AS reservaciones,
                (SELECT COALESCE(AVG(r.noches), 0)
                   FROM reservacion r
                   JOIN habitacion x ON x.id_habitacion = r.id_habitacion
                  WHERE x.id_tipo = t.id_tipo AND r.estado <> 'Cancelada'
                    AND r.fecha_entrada >= :desde AND r.fecha_entrada < :hastaExcl
                    AND (:idHotel = 0 OR x.id_hotel = :idHotel)) AS promnoches
            FROM tipo_habitacion t
            ORDER BY ingresos DESC, t.nombre
            """, nativeQuery = true)
    List<GerenteViews.TipoView> gerentePorTipo(@Param("desde") LocalDate desde,
                                               @Param("hastaExcl") LocalDate hastaExcl,
                                               @Param("desdeTs") LocalDateTime desdeTs,
                                               @Param("hastaTs") LocalDateTime hastaTs,
                                               @Param("idHotel") long idHotel);

    /** Ingresos aprobados por día (el servicio los agrupa por mes si el rango es largo). */
    @Query(value = """
            SELECT DATE_FORMAT(p.fecha, '%Y-%m-%d') AS dia, SUM(p.monto) AS ingresos
            FROM pago p
            JOIN reservacion r ON r.id_reservacion = p.id_reservacion
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE p.estado = 'Aprobado'
              AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY DATE_FORMAT(p.fecha, '%Y-%m-%d')
            ORDER BY dia
            """, nativeQuery = true)
    List<GerenteViews.PeriodoView> gerenteIngresosPorDia(@Param("desdeTs") LocalDateTime desdeTs,
                                                         @Param("hastaTs") LocalDateTime hastaTs,
                                                         @Param("idHotel") long idHotel);

    /**
     * Reservaciones registradas en el periodo por estado. «Vencidas» = Pendiente_Pago cuya retención ya pasó
     * (:limite = ahora - minutos de retención) y que el scheduler aún no cancela. «Canceladas sin pago» = canceladas
     * que nunca tuvieron un pago aprobado (incluye las que el scheduler canceló por falta de pago).
     */
    @Query(value = """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN r.estado = 'Confirmada' THEN 1 ELSE 0 END), 0) AS confirmadas,
                COALESCE(SUM(CASE WHEN r.estado = 'Pendiente' THEN 1 ELSE 0 END), 0) AS pendientes,
                COALESCE(SUM(CASE WHEN r.estado = 'Pendiente_Pago' AND r.fecha_registro >= :limite THEN 1 ELSE 0 END), 0) AS pendientepago,
                COALESCE(SUM(CASE WHEN r.estado = 'Pendiente_Pago' AND r.fecha_registro < :limite THEN 1 ELSE 0 END), 0) AS vencidas,
                COALESCE(SUM(CASE WHEN r.estado = 'Cancelada' THEN 1 ELSE 0 END), 0) AS canceladas,
                COALESCE(SUM(CASE WHEN r.estado = 'Cancelada'
                                   AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.id_reservacion = r.id_reservacion AND p.estado = 'Aprobado')
                                  THEN 1 ELSE 0 END), 0) AS canceladassinpago,
                COALESCE(SUM(CASE WHEN r.estado = 'Confirmada' THEN r.costo_total ELSE 0 END), 0) AS montoconfirmado,
                COALESCE(SUM(CASE WHEN r.estado = 'Cancelada' THEN r.costo_total ELSE 0 END), 0) AS montocancelado
            FROM reservacion r
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE r.fecha_registro >= :desdeTs AND r.fecha_registro < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            """, nativeQuery = true)
    List<GerenteViews.EstadosView> gerenteEstados(@Param("desdeTs") LocalDateTime desdeTs,
                                                  @Param("hastaTs") LocalDateTime hastaTs,
                                                  @Param("limite") LocalDateTime limite,
                                                  @Param("idHotel") long idHotel);

    // ====================== Visión ampliada del gerente ======================

    /** Ingresos aprobados en un rango (para comparar contra el periodo anterior). */
    @Query(value = """
            SELECT COALESCE(SUM(p.monto), 0)
            FROM pago p
            JOIN reservacion r ON r.id_reservacion = p.id_reservacion
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE p.estado = 'Aprobado'
              AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            """, nativeQuery = true)
    BigDecimal gerenteIngresosEntre(@Param("desdeTs") LocalDateTime desdeTs,
                                    @Param("hastaTs") LocalDateTime hastaTs,
                                    @Param("idHotel") long idHotel);

    /** Evolución anual de ingresos aprobados (todo el historial). */
    @Query(value = """
            SELECT CAST(YEAR(p.fecha) AS CHAR) AS clave, COUNT(*) AS total, SUM(p.monto) AS monto
            FROM pago p
            JOIN reservacion r ON r.id_reservacion = p.id_reservacion
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE p.estado = 'Aprobado'
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY clave
            ORDER BY MIN(p.fecha)
            """, nativeQuery = true)
    List<GerenteViews.ConteoView> gerentePorAnio(@Param("idHotel") long idHotel);

    /** Temporada: ingresos aprobados por mes del año (1-12) dentro del periodo. */
    @Query(value = """
            SELECT CAST(MONTH(p.fecha) AS CHAR) AS clave, COUNT(*) AS total, SUM(p.monto) AS monto
            FROM pago p
            JOIN reservacion r ON r.id_reservacion = p.id_reservacion
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE p.estado = 'Aprobado'
              AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY clave
            ORDER BY MIN(p.fecha)
            """, nativeQuery = true)
    List<GerenteViews.ConteoView> gerentePorMesDelAnio(@Param("desdeTs") LocalDateTime desdeTs,
                                                       @Param("hastaTs") LocalDateTime hastaTs,
                                                       @Param("idHotel") long idHotel);

    /** Ciudades de procedencia de las reservaciones vigentes que inician en el periodo (top 6). */
    @Query(value = """
            SELECT COALESCE(NULLIF(TRIM(r.ciudad_procedencia), ''), 'Sin dato') AS clave,
                   COUNT(*) AS total, COALESCE(SUM(r.costo_total), 0) AS monto
            FROM reservacion r
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE r.estado <> 'Cancelada'
              AND r.fecha_entrada >= :desde AND r.fecha_entrada < :hastaExcl
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY clave
            ORDER BY total DESC, clave
            LIMIT 6
            """, nativeQuery = true)
    List<GerenteViews.ConteoView> gerenteProcedencia(@Param("desde") LocalDate desde,
                                                     @Param("hastaExcl") LocalDate hastaExcl,
                                                     @Param("idHotel") long idHotel);

    /** Reservaciones registradas en el periodo según su origen (WEB / IA). */
    @Query(value = """
            SELECT r.origen AS clave, COUNT(*) AS total, COALESCE(SUM(r.costo_total), 0) AS monto
            FROM reservacion r
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE r.fecha_registro >= :desdeTs AND r.fecha_registro < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY r.origen
            ORDER BY total DESC
            """, nativeQuery = true)
    List<GerenteViews.ConteoView> gerenteOrigen(@Param("desdeTs") LocalDateTime desdeTs,
                                                @Param("hastaTs") LocalDateTime hastaTs,
                                                @Param("idHotel") long idHotel);

    /** Pagos aprobados del periodo por método de pago. */
    @Query(value = """
            SELECT p.metodo AS clave, COUNT(*) AS total, SUM(p.monto) AS monto
            FROM pago p
            JOIN reservacion r ON r.id_reservacion = p.id_reservacion
            JOIN habitacion x ON x.id_habitacion = r.id_habitacion
            WHERE p.estado = 'Aprobado'
              AND p.fecha >= :desdeTs AND p.fecha < :hastaTs
              AND (:idHotel = 0 OR x.id_hotel = :idHotel)
            GROUP BY p.metodo
            ORDER BY monto DESC
            """, nativeQuery = true)
    List<GerenteViews.ConteoView> gerenteMetodos(@Param("desdeTs") LocalDateTime desdeTs,
                                                 @Param("hastaTs") LocalDateTime hastaTs,
                                                 @Param("idHotel") long idHotel);
}
