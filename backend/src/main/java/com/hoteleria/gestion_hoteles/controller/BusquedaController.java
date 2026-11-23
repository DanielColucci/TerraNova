package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.dto.ResultadoBusqueda;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Historia 1: búsqueda avanzada y filtrado de disponibilidad (REST). */
@RestController
@RequestMapping("/api/busqueda")
public class BusquedaController {

    private final ConsultaService consultaService;
    private final HotelService hotelService;
    private final TipoHabitacionService tipoService;

    public BusquedaController(ConsultaService consultaService, HotelService hotelService,
                              TipoHabitacionService tipoService) {
        this.consultaService = consultaService;
        this.hotelService = hotelService;
        this.tipoService = tipoService;
    }

    @GetMapping
    public Map<String, Object> buscar(@RequestParam(required = false) String ciudad,
                                      @RequestParam(required = false) Long idHotel,
                                      @RequestParam(required = false) Long idTipo,
                                      @RequestParam(required = false) Integer capacidad,
                                      @RequestParam(required = false) BigDecimal precioMax,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entrada,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salida,
                                      @RequestParam(required = false, defaultValue = "precio") String orden) {
        List<ResultadoBusqueda> resultados = Collections.emptyList();
        String aviso = validarFechas(entrada, salida);
        long noches = (entrada != null && salida != null && aviso == null) ? ChronoUnit.DAYS.between(entrada, salida) : 0;

        if (aviso == null) {
            resultados = new ArrayList<>(consultaService.buscarDisponibilidad(
                    ciudad, idHotel, idTipo, capacidad, precioMax,
                    noches > 0 ? entrada : null, noches > 0 ? salida : null));
            ordenar(resultados, orden);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hoy", LocalDate.now());
        m.put("noches", noches);
        m.put("aviso", aviso);
        m.put("hoteles", hotelService.listarActivos().stream().map(ApiMapper::hotel).toList());
        m.put("tipos", tipoService.listar().stream().map(ApiMapper::tipo).toList());
        m.put("ciudades", consultaService.ciudadesActivas());
        m.put("resultados", resultados);
        return m;
    }

    private String validarFechas(LocalDate entrada, LocalDate salida) {
        if (entrada == null && salida == null) {
            return null;
        }
        if (entrada == null || salida == null) {
            return "Para calcular el total estimado indica tanto la fecha de llegada como la de salida.";
        }
        if (entrada.isBefore(LocalDate.now())) {
            return "La fecha de llegada no puede ser anterior a hoy.";
        }
        if (!salida.isAfter(entrada)) {
            return "La fecha de salida debe ser posterior a la fecha de llegada.";
        }
        return null;
    }

    private void ordenar(List<ResultadoBusqueda> lista, String orden) {
        Comparator<ResultadoBusqueda> porHotel = Comparator.comparing(ResultadoBusqueda::getHotel)
                .thenComparing(ResultadoBusqueda::getHabitacion);
        switch (orden) {
            case "precio-desc" -> lista.sort(Comparator.comparing(ResultadoBusqueda::getPrecio).reversed().thenComparing(porHotel));
            case "capacidad" -> lista.sort(Comparator.comparing(ResultadoBusqueda::getCapacidad).reversed().thenComparing(porHotel));
            case "hotel" -> lista.sort(porHotel);
            default -> lista.sort(Comparator.comparing(ResultadoBusqueda::getPrecio).thenComparing(porHotel));
        }
    }
}
