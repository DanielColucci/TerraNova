package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.dto.CheckInForm;
import com.hoteleria.gestion_hoteles.dto.CheckOutForm;
import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.Estancia;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.MetodoPago;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.service.EstanciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Historia 2 (check-in) e Historia 3 (check-out): reglas de negocio y cálculo del folio. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Recepción: check-in y check-out")
class EstanciaServiceTest {

    @Mock EstanciaRepository estanciaRepository;
    @Mock HabitacionRepository habitacionRepository;

    EstanciaService service;
    Hotel hotel;
    TipoHabitacion doble; // capacidad 2, $1,200 por noche
    Habitacion hab;

    @BeforeEach
    void preparar() {
        service = new EstanciaService(estanciaRepository, habitacionRepository);
        hotel = Datos.hotel(1L, "TerraNova Centro", true);
        doble = Datos.tipo(1L, "Doble", 2, "1200.00");
        hab = Datos.habitacion(10L, hotel, doble, "102", EstadoHabitacion.Disponible);
    }

    private CheckInForm formCheckIn(int huespedes) {
        CheckInForm f = new CheckInForm();
        f.setIdHabitacion(10L);
        f.setHuesped("  Mariana López  ");
        f.setIdentificacion(" INE-123 ");
        f.setNumHuespedes(huespedes);
        f.setFechaSalidaPrevista(LocalDate.now().plusDays(2));
        return f;
    }

    private Estancia estanciaActiva(int diasAtras) {
        Estancia e = new Estancia();
        e.setIdEstancia(5L);
        e.setHabitacion(hab);
        e.setHuesped("Mariana López");
        e.setFechaEntrada(LocalDateTime.now().minusDays(diasAtras));
        e.setFechaSalidaPrevista(LocalDate.now().plusDays(1));
        e.setPrecioNoche(new BigDecimal("1200.00"));
        hab.setEstado(EstadoHabitacion.Ocupada);
        return e;
    }

    private CheckOutForm formCheckOut(String cargos) {
        CheckOutForm f = new CheckOutForm();
        f.setCargosExtra(cargos == null ? null : new BigDecimal(cargos));
        f.setMetodoPago(MetodoPago.Tarjeta);
        return f;
    }

    // ------------------------------------------------------------ CHECK-IN

    @Test
    @DisplayName("Check-in válido: crea la estancia, congela la tarifa y pone la habitación en Ocupada")
    void checkInValido() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));
        when(estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(10L)).thenReturn(false);
        when(estanciaRepository.save(any(Estancia.class))).thenAnswer(inv -> inv.getArgument(0));

        Estancia e = service.registrarCheckIn(formCheckIn(2));

        assertThat(hab.getEstado()).isEqualTo(EstadoHabitacion.Ocupada);
        assertThat(e.getHuesped()).isEqualTo("Mariana López");          // se recortan espacios
        assertThat(e.getIdentificacion()).isEqualTo("INE-123");
        assertThat(e.getPrecioNoche()).isEqualByComparingTo("1200.00");  // tarifa congelada
        assertThat(e.isActiva()).isTrue();
        verify(habitacionRepository).save(hab);
    }

    @Test
    @DisplayName("Check-in: habitación inexistente")
    void checkInHabitacionInexistente() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarCheckIn(formCheckIn(1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no existe");
        verify(estanciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Check-in: un hotel inactivo no admite huéspedes")
    void checkInHotelInactivo() {
        hotel.setActivo(false);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));

        assertThatThrownBy(() -> service.registrarCheckIn(formCheckIn(1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
        assertThat(hab.getEstado()).isEqualTo(EstadoHabitacion.Disponible);
    }

    @Test
    @DisplayName("Check-in: solo se asigna una habitación Disponible")
    void checkInHabitacionNoDisponible() {
        hab.setEstado(EstadoHabitacion.Mantenimiento);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));

        assertThatThrownBy(() -> service.registrarCheckIn(formCheckIn(1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("Check-in: no se puede asignar una habitación que ya tiene huésped")
    void checkInHabitacionConHuesped() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));
        when(estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.registrarCheckIn(formCheckIn(1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya tiene un huésped");
    }

    @Test
    @DisplayName("Check-in: no se puede superar la capacidad del tipo de habitación")
    void checkInExcedeCapacidad() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));
        when(estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.registrarCheckIn(formCheckIn(3)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("máximo 2");
        assertThat(hab.getEstado()).isEqualTo(EstadoHabitacion.Disponible);
    }

    // ------------------------------------------------------------ CHECK-OUT

    @Test
    @DisplayName("Check-out: total = noches × tarifa + cargos extra; la habitación pasa a Mantenimiento")
    void checkOutCalculaTotal() {
        Estancia e = estanciaActiva(3);
        when(estanciaRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(e));
        when(estanciaRepository.save(any(Estancia.class))).thenAnswer(inv -> inv.getArgument(0));

        Estancia cerrada = service.registrarCheckOut(5L, formCheckOut("350.50"));

        assertThat(cerrada.getNoches()).isEqualTo(3);
        assertThat(cerrada.getTotal()).isEqualByComparingTo("3950.50"); // 3 × 1200 + 350.50
        assertThat(cerrada.getMetodoPago()).isEqualTo(MetodoPago.Tarjeta);
        assertThat(cerrada.isActiva()).isFalse();
        assertThat(hab.getEstado()).isEqualTo(EstadoHabitacion.Mantenimiento);
    }

    @Test
    @DisplayName("Check-out el mismo día del check-in se cobra como mínimo 1 noche")
    void checkOutMinimoUnaNoche() {
        Estancia e = estanciaActiva(0);

        assertThat(service.nochesTranscurridas(e)).isEqualTo(1);
        assertThat(service.importeHospedaje(e)).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Check-out sin cargos extra (null) se toma como 0")
    void checkOutCargosNulos() {
        Estancia e = estanciaActiva(2);
        when(estanciaRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(e));
        when(estanciaRepository.save(any(Estancia.class))).thenAnswer(inv -> inv.getArgument(0));

        Estancia cerrada = service.registrarCheckOut(5L, formCheckOut(null));

        assertThat(cerrada.getCargosExtra()).isEqualByComparingTo("0");
        assertThat(cerrada.getTotal()).isEqualByComparingTo("2400.00");
    }

    @Test
    @DisplayName("Check-out: una estancia ya cerrada no puede cerrarse otra vez")
    void checkOutDoble() {
        Estancia e = estanciaActiva(2);
        e.setFechaSalidaReal(LocalDateTime.now());
        when(estanciaRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.registrarCheckOut(5L, formCheckOut("0")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya tiene check-out");
        verify(estanciaRepository, never()).save(any());
    }

    // ------------------------------------------------------------ REGULARIZAR

    @Test
    @DisplayName("Regularizar: una habitación Ocupada sin huésped pasa a Mantenimiento")
    void regularizarOcupadaSinRegistro() {
        hab.setEstado(EstadoHabitacion.Ocupada);
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));
        when(estanciaRepository.existsByHabitacion_IdHabitacionAndFechaSalidaRealIsNull(10L)).thenReturn(false);
        when(habitacionRepository.save(hab)).thenReturn(hab);

        Habitacion r = service.regularizarOcupadaSinRegistro(10L);

        assertThat(r.getEstado()).isEqualTo(EstadoHabitacion.Mantenimiento);
    }

    @Test
    @DisplayName("Regularizar: si la habitación no está Ocupada no hay nada que regularizar")
    void regularizarNoAplica() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab)); // Disponible

        assertThatThrownBy(() -> service.regularizarOcupadaSinRegistro(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no requiere regularización");
    }
}
