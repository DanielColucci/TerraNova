package com.hoteleria.gestion_hoteles.dto;

/** Proyecciones nativas del panel gerencial (Fase 4). Los alias de las consultas van en minúsculas. */
public interface GerenteViews {

    /** Por hotel: ingresos aprobados del periodo, noches vendidas y ocupación actual. */
    interface HotelView {
        Number getIdhotel();
        String getHotel();
        String getCiudad();
        Number getHabitaciones();
        Number getOcupadasahora();
        Number getNochesvendidas();
        Number getIngresos();
        Number getPagos();
    }

    /** Por tipo de habitación: ingresos aprobados, reservaciones y promedio de estancia. */
    interface TipoView {
        Number getIdtipo();
        String getTipo();
        Number getIngresos();
        Number getPagos();
        Number getReservaciones();
        Number getPromnoches();
    }

    /** Ingreso aprobado por día (yyyy-MM-dd). */
    interface PeriodoView {
        String getDia();
        Number getIngresos();
    }

    /** Desglose de reservaciones por estado (una sola fila). */
    interface EstadosView {
        Number getTotal();
        Number getConfirmadas();
        Number getPendientes();
        Number getPendientepago();
        Number getVencidas();
        Number getCanceladas();
        Number getCanceladassinpago();
        Number getMontoconfirmado();
        Number getMontocancelado();
    }

    /** Conteo/monto por una clave de texto (año, mes, ciudad, origen o método de pago). */
    interface ConteoView {
        String getClave();
        Number getTotal();
        Number getMonto();
    }
}
