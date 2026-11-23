package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRUD REST de tipos de habitación (solo ADMINISTRADOR). */
@RestController
@RequestMapping("/api/tipos")
public class TipoHabitacionController {

    private final TipoHabitacionService tipoService;
    private final HabitacionService habitacionService;

    public TipoHabitacionController(TipoHabitacionService tipoService, HabitacionService habitacionService) {
        this.tipoService = tipoService;
        this.habitacionService = habitacionService;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return tipoService.listar().stream().map(ApiMapper::tipo).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        TipoHabitacion t = tipoService.buscar(id);
        var habitaciones = habitacionService.listarPorTipo(id).stream().map(ApiMapper::habitacion).toList();
        Map<String, Object> tipoMap = ApiMapper.tipo(t);
        tipoMap.put("totalHabitaciones", habitaciones.size());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipoMap);
        m.put("habitaciones", habitaciones);
        return m;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody TipoHabitacion tipo) {
        TipoHabitacion guardado = tipoService.crear(tipo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Tipo de habitación «" + guardado.getNombre() + "» registrado correctamente.");
        m.put("tipo", ApiMapper.tipo(guardado));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(@PathVariable Long id, @Valid @RequestBody TipoHabitacion tipo) {
        TipoHabitacion guardado = tipoService.actualizar(id, tipo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Tipo de habitación «" + guardado.getNombre() + "» actualizado correctamente.");
        m.put("tipo", ApiMapper.tipo(guardado));
        return m;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable Long id) {
        String nombre = tipoService.buscar(id).getNombre();
        tipoService.eliminar(id);
        return Map.of("mensaje", "Tipo de habitación «" + nombre + "» eliminado correctamente.");
    }
}
