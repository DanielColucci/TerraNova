package com.hoteleria.gestion_hoteles.dto;

/** Habitaciones disponibles de un hotel (tarjeta del panel principal). */
public record DisponiblesPorHotel(String hotel, long disponibles) {

    // Getters estilo JavaBean para que Thymeleaf siga usando ${d.hotel} y ${d.disponibles}
    public String getHotel() { return hotel; }
    public long getDisponibles() { return disponibles; }
}
