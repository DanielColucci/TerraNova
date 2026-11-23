package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.dto.HabitacionForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRUD REST de habitaciones (solo ADMINISTRADOR). */
@RestController
@RequestMapping("/api/habitaciones")
public class HabitacionController {

    private final HabitacionService habitacionService;
    private final HotelService hotelService;
    private final TipoHabitacionService tipoService;

    public HabitacionController(HabitacionService habitacionService,
                                HotelService hotelService,
                                TipoHabitacionService tipoService) {
        this.habitacionService = habitacionService;
        this.hotelService = hotelService;
        this.tipoService = tipoService;
    }

    @GetMapping
    public Map<String, Object> listar() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("habitaciones", habitacionService.listar().stream().map(ApiMapper::habitacion).toList());
        m.put("hoteles", hotelService.listar().stream().map(ApiMapper::hotel).toList());
        return m;
    }

    @GetMapping("/catalogos")
    public Map<String, Object> catalogos(@RequestParam(required = false) Long idHotelActual) {
        List<Hotel> hoteles = new ArrayList<>(hotelService.listarActivos());
        if (idHotelActual != null && hoteles.stream().noneMatch(h -> idHotelActual.equals(h.getIdHotel()))) {
            try {
                hoteles.add(hotelService.buscar(idHotelActual));
            } catch (RuntimeException ignored) {
                // hotel inexistente
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hoteles", hoteles.stream().map(ApiMapper::hotel).toList());
        m.put("tipos", tipoService.listar().stream().map(ApiMapper::tipo).toList());
        m.put("estados", Arrays.stream(EstadoHabitacion.values()).map(Enum::name).toList());
        return m;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        return ApiMapper.habitacion(habitacionService.buscar(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody HabitacionForm form) {
        var guardada = habitacionService.crear(form);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Habitación " + guardada.getNumero() + " registrada en «" + guardada.getHotel().getNombre() + "».");
        m.put("habitacion", ApiMapper.habitacion(guardada));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable Long id, @Valid @RequestBody HabitacionForm form) {
        var guardada = habitacionService.actualizar(id, form);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Habitación " + guardada.getNumero() + " de «" + guardada.getHotel().getNombre() + "» actualizada correctamente.");
        m.put("habitacion", ApiMapper.habitacion(guardada));
        return m;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Long id) {
        habitacionService.eliminar(id);
        return Map.of("mensaje", "Habitación eliminada correctamente.");
    }
}
