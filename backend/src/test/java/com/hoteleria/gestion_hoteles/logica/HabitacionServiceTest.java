package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.dto.DisponiblesPorHotel;
import com.hoteleria.gestion_hoteles.dto.HabitacionForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import com.hoteleria.gestion_hoteles.service.HabitacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRUD de habitaciones y cambios de estado físico (Historia 4 del recepcionista). */
@ExtendWith(MockitoExtension.class)
@DisplayName("Habitaciones: reglas de negocio y estados")
class HabitacionServiceTest {

    @Mock HabitacionRepository habitacionRepository;
    @Mock HotelRepository hotelRepository;
    @Mock TipoHabitacionRepository tipoRepository;

    HabitacionService service;
    Hotel hotel;
    TipoHabitacion tipo;

    @BeforeEach
    void preparar() {
        service = new HabitacionService(habitacionRepository, hotelRepository, tipoRepository);
        hotel = Datos.hotel(1L, "TerraNova Centro", true);
        tipo = Datos.tipo(1L, "Doble", 2, "1200.00");
    }

    private HabitacionForm form(String numero) {
        HabitacionForm f = new HabitacionForm();
        f.setIdHotel(1L);
        f.setIdTipo(1L);
        f.setNumero(numero);
        f.setPiso(2);
        f.setEstado(EstadoHabitacion.Disponible);
        return f;
    }

    private Habitacion habitacion(EstadoHabitacion estado) {
        return Datos.habitacion(10L, hotel, tipo, "201", estado);
    }

    // ------------------------------------------------------------ CREAR

    @Test
    @DisplayName("Crear: guarda la habitación y recorta espacios del número")
    void crearValida() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCase(1L, "301")).thenReturn(false);
        when(habitacionRepository.saveAndFlush(any(Habitacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Habitacion h = service.crear(form(" 301 "));

        assertThat(h.getNumero()).isEqualTo("301");
        assertThat(h.getHotel()).isSameAs(hotel);
        assertThat(h.getTipo()).isSameAs(tipo);
    }

    @Test
    @DisplayName("Crear: el número no puede repetirse dentro del mismo hotel")
    void crearNumeroDuplicado() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCase(1L, "101")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(form("101")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya existe")
                .extracting("campo").isEqualTo("numero");
        verify(habitacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear: un hotel inactivo no recibe nuevas habitaciones")
    void crearEnHotelInactivo() {
        hotel.setActivo(false);
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));

        assertThatThrownBy(() -> service.crear(form("301")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    @DisplayName("Crear: hotel inexistente")
    void crearHotelInexistente() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(form("301")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("hotel seleccionado no existe");
    }

    @Test
    @DisplayName("Crear: la restricción única de la BD también se traduce a un mensaje claro")
    void crearRespaldoDeBaseDeDatos() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCase(1L, "301")).thenReturn(false);
        when(habitacionRepository.saveAndFlush(any(Habitacion.class)))
                .thenThrow(new DataIntegrityViolationException("uk_habitacion_hotel_numero"));

        assertThatThrownBy(() -> service.crear(form("301")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya existe");
    }

    // ------------------------------------------------------------ ACTUALIZAR / ELIMINAR

    @Test
    @DisplayName("Actualizar: no puede tomar el número de otra habitación del mismo hotel")
    void actualizarNumeroDuplicado() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(habitacion(EstadoHabitacion.Disponible)));
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(habitacionRepository.existsByHotel_IdHotelAndNumeroIgnoreCaseAndIdHabitacionNot(1L, "101", 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(10L, form("101")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya existe");
    }

    @Test
    @DisplayName("Eliminar: una habitación inexistente muestra un mensaje claro")
    void eliminarInexistente() {
        when(habitacionRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("no existe");
        verify(habitacionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar: si la habitación tiene estancias asociadas se explica por qué no se puede")
    void eliminarConInformacionAsociada() {
        when(habitacionRepository.existsById(10L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk_estancia_habitacion"))
                .when(habitacionRepository).deleteById(10L);

        assertThatThrownBy(() -> service.eliminar(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("información asociada");
    }

    // ------------------------------------------------------------ ESTADOS

    @Test
    @DisplayName("Disponible → Mantenimiento")
    void aMantenimiento() {
        Habitacion h = habitacion(EstadoHabitacion.Disponible);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(h));
        when(habitacionRepository.save(h)).thenReturn(h);

        assertThat(service.enviarAMantenimiento(10L).getEstado()).isEqualTo(EstadoHabitacion.Mantenimiento);
    }

    @Test
    @DisplayName("Una habitación Ocupada NO puede enviarse a mantenimiento (primero check-out)")
    void ocupadaNoVaAMantenimiento() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(habitacion(EstadoHabitacion.Ocupada)));

        assertThatThrownBy(() -> service.enviarAMantenimiento(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("check-out");
    }

    @Test
    @DisplayName("Una habitación que ya está en mantenimiento no se vuelve a enviar")
    void yaEnMantenimiento() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(habitacion(EstadoHabitacion.Mantenimiento)));

        assertThatThrownBy(() -> service.enviarAMantenimiento(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya está en mantenimiento");
    }

    @Test
    @DisplayName("Un hotel inactivo no admite cambios de estado operativos")
    void hotelInactivoNoCambiaEstados() {
        hotel.setActivo(false);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(habitacion(EstadoHabitacion.Disponible)));

        assertThatThrownBy(() -> service.enviarAMantenimiento(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    @DisplayName("Mantenimiento → Disponible")
    void aDisponible() {
        Habitacion h = habitacion(EstadoHabitacion.Mantenimiento);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(h));
        when(habitacionRepository.save(h)).thenReturn(h);

        assertThat(service.marcarDisponible(10L).getEstado()).isEqualTo(EstadoHabitacion.Disponible);
    }

    @Test
    @DisplayName("Solo una habitación en mantenimiento puede volver a Disponible")
    void soloMantenimientoVuelveADisponible() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(habitacion(EstadoHabitacion.Ocupada)));

        assertThatThrownBy(() -> service.marcarDisponible(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Solo una habitación en mantenimiento");
    }

    // ------------------------------------------------------------ PANEL: disponibles por hotel

    @Test
    @DisplayName("Panel: cuenta solo habitaciones Disponibles de hoteles activos, ordenado por hotel")
    void disponiblesPorHotel() {
        Hotel norte = Datos.hotel(2L, "TerraNova Norte", true);
        Hotel cerrado = Datos.hotel(3L, "TerraNova Cerrado", false);
        when(habitacionRepository.findAllConDetalle()).thenReturn(List.of(
                Datos.habitacion(1L, norte, tipo, "101", EstadoHabitacion.Disponible),
                Datos.habitacion(2L, hotel, tipo, "101", EstadoHabitacion.Disponible),
                Datos.habitacion(3L, hotel, tipo, "102", EstadoHabitacion.Disponible),
                Datos.habitacion(4L, hotel, tipo, "103", EstadoHabitacion.Ocupada),
                Datos.habitacion(5L, hotel, tipo, "104", EstadoHabitacion.Mantenimiento),
                Datos.habitacion(6L, cerrado, tipo, "101", EstadoHabitacion.Disponible)));

        List<DisponiblesPorHotel> r = service.disponiblesPorHotel();

        assertThat(r).containsExactly(
                new DisponiblesPorHotel("TerraNova Centro", 2),
                new DisponiblesPorHotel("TerraNova Norte", 1));
    }
}
