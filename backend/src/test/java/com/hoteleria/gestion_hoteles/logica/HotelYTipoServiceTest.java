package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import com.hoteleria.gestion_hoteles.service.HotelService;
import com.hoteleria.gestion_hoteles.service.TipoHabitacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de negocio de los catálogos: hoteles y tipos de habitación. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Hoteles y tipos de habitación: reglas de negocio")
class HotelYTipoServiceTest {

    @Mock HotelRepository hotelRepository;
    @Mock TipoHabitacionRepository tipoRepository;
    @Mock HabitacionRepository habitacionRepository;

    HotelService hotelService;
    TipoHabitacionService tipoService;

    @BeforeEach
    void preparar() {
        hotelService = new HotelService(hotelRepository, habitacionRepository);
        tipoService = new TipoHabitacionService(tipoRepository, habitacionRepository);
    }

    // ------------------------------------------------------------ HOTELES

    @Test
    @DisplayName("Hotel: al crear se recortan espacios, se ignora el id recibido y queda Activo por defecto")
    void crearHotelNormaliza() {
        Hotel nuevo = Datos.hotel(99L, "  Hotel Nuevo  ", true);
        nuevo.setCiudad(" Toluca ");
        nuevo.setActivo(null);
        when(hotelRepository.saveAndFlush(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

        Hotel guardado = hotelService.crear(nuevo);

        assertThat(guardado.getIdHotel()).isNull();
        assertThat(guardado.getNombre()).isEqualTo("Hotel Nuevo");
        assertThat(guardado.getCiudad()).isEqualTo("Toluca");
        assertThat(guardado.getActivo()).isTrue();
    }

    @Test
    @DisplayName("Hotel: no se elimina si tiene habitaciones y se sugiere marcarlo Inactivo")
    void eliminarHotelConHabitaciones() {
        Hotel h = Datos.hotel(1L, "TerraNova Centro", true);
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(h));
        when(habitacionRepository.countByHotel_IdHotel(1L)).thenReturn(8L);

        assertThatThrownBy(() -> hotelService.eliminar(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("8 habitaciones asociadas")
                .hasMessageContaining("Inactivo");
        verify(hotelRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Hotel: con una sola habitación el mensaje va en singular")
    void eliminarHotelUnaHabitacion() {
        Hotel h = Datos.hotel(1L, "TerraNova Centro", true);
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(h));
        when(habitacionRepository.countByHotel_IdHotel(1L)).thenReturn(1L);

        assertThatThrownBy(() -> hotelService.eliminar(1L))
                .hasMessageContaining("1 habitación asociada");
    }

    @Test
    @DisplayName("Hotel: sin habitaciones sí se puede eliminar")
    void eliminarHotelVacio() {
        Hotel h = Datos.hotel(1L, "TerraNova Centro", true);
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(h));
        when(habitacionRepository.countByHotel_IdHotel(1L)).thenReturn(0L);

        hotelService.eliminar(1L);

        verify(hotelRepository).delete(h);
    }

    @Test
    @DisplayName("Hotel: buscar uno inexistente lanza RecursoNoEncontrado con ruta de regreso")
    void buscarHotelInexistente() {
        when(hotelRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.buscar(7L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        ex -> assertThat(ex.getRedireccion()).isEqualTo("/hoteles"));
    }

    // ------------------------------------------------------------ TIPOS

    @Test
    @DisplayName("Tipo: no se permiten dos tipos con el mismo nombre (sin importar mayúsculas)")
    void crearTipoDuplicado() {
        TipoHabitacion t = Datos.tipo(null, "  doble ", 2, "1200");
        when(tipoRepository.existsByNombreIgnoreCase("doble")).thenReturn(true);

        assertThatThrownBy(() -> tipoService.crear(t))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe");
        verify(tipoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tipo: actualizar no puede tomar el nombre de otro tipo")
    void actualizarTipoDuplicado() {
        TipoHabitacion existente = Datos.tipo(1L, "Doble", 2, "1200");
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(tipoRepository.existsByNombreIgnoreCaseAndIdTipoNot("Suite", 1L)).thenReturn(true);

        assertThatThrownBy(() -> tipoService.actualizar(1L, Datos.tipo(null, "Suite", 4, "2500")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("otro tipo");
    }

    @Test
    @DisplayName("Tipo: no se elimina si lo usa alguna habitación")
    void eliminarTipoEnUso() {
        TipoHabitacion t = Datos.tipo(1L, "Doble", 2, "1200");
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(t));
        when(habitacionRepository.countByTipo_IdTipo(1L)).thenReturn(5L);

        assertThatThrownBy(() -> tipoService.eliminar(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("5 habitaciones");
        verify(tipoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Tipo: sin habitaciones sí se puede eliminar")
    void eliminarTipoLibre() {
        TipoHabitacion t = Datos.tipo(1L, "Doble", 2, "1200");
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(t));
        when(habitacionRepository.countByTipo_IdTipo(1L)).thenReturn(0L);

        tipoService.eliminar(1L);

        verify(tipoRepository).delete(t);
    }
}
