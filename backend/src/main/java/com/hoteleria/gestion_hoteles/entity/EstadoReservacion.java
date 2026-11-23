package com.hoteleria.gestion_hoteles.entity;

/** Estado de una reservación. Cancelada libera la habitación para esas fechas. */
public enum EstadoReservacion {
    Confirmada,
    Pendiente,
    /** Reservada desde el chat/web: la habitación se retiene 15 min hasta que se paga (pago simulado). */
    Pendiente_Pago,
    Cancelada
}
