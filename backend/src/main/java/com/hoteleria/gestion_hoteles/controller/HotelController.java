package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRUD REST de hoteles (solo ADMINISTRADOR). */
@RestController
@RequestMapping("/api/hoteles")
public class HotelController {

    private final HotelService hotelService;
    private final HabitacionService habitacionService;

    public HotelController(HotelService hotelService, HabitacionService habitacionService) {
        this.hotelService = hotelService;
        this.habitacionService = habitacionService;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return hotelService.listar().stream().map(ApiMapper::hotel).toList();
    }

    @GetMapping("/activos")
    public List<Map<String, Object>> listarActivos() {
        return hotelService.listarActivos().stream().map(ApiMapper::hotel).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        Hotel h = hotelService.buscar(id);
        var habitaciones = habitacionService.listarPorHotel(id).stream().map(ApiMapper::habitacion).toList();
        Map<String, Object> hotelMap = ApiMapper.hotel(h);
        hotelMap.put("totalHabitaciones", habitaciones.size());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hotel", hotelMap);
        m.put("habitaciones", habitaciones);
        return m;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody Hotel hotel) {
        Hotel guardado = hotelService.crear(hotel);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Hotel «" + guardado.getNombre() + "» registrado correctamente.");
        m.put("hotel", ApiMapper.hotel(guardado));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable Long id, @Valid @RequestBody Hotel hotel) {
        Hotel guardado = hotelService.actualizar(id, hotel);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Hotel «" + guardado.getNombre() + "» actualizado correctamente.");
        m.put("hotel", ApiMapper.hotel(guardado));
        return m;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Long id) {
        String nombre = hotelService.buscar(id).getNombre();
        hotelService.eliminar(id);
        return Map.of("mensaje", "Hotel «" + nombre + "» eliminado correctamente.");
    }
}
