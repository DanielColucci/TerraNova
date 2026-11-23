package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.service.ConsultaService;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** API del panel general del administrador (rol ADMINISTRADOR). */
@RestController
@RequestMapping("/api/admin")
public class HomeController {

    private final ConsultaService consultaService;
    private final HabitacionService habitacionService;
    private final HotelService hotelService;

    public HomeController(ConsultaService consultaService,
                          HabitacionService habitacionService,
                          HotelService hotelService) {
        this.consultaService = consultaService;
        this.habitacionService = habitacionService;
        this.hotelService = hotelService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resumen", consultaService.resumen());
        m.put("habitaciones", habitacionService.listar().stream().map(ApiMapper::habitacion).toList());
        m.put("disponiblesPorHotel", habitacionService.disponiblesPorHotel());
        m.put("hoteles", hotelService.listar().stream().map(ApiMapper::hotel).toList());
        return m;
    }
}
