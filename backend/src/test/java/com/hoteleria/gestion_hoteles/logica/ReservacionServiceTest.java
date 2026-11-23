package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.entity.EstadoHabitacion;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Habitacion;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.ReservacionRepository;
import com.hoteleria.gestion_hoteles.service.ReservacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de las reservaciones: las mismas las usan la pantalla web y el asistente de IA. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Reservaciones: reglas de negocio")
class ReservacionServiceTest {

    @Mock ReservacionRepository reservacionRepository;
    @Mock HabitacionRepository habitacionRepository;
    @Mock EstanciaRepository estanciaRepository;

    ReservacionService service;
    Hotel hotel;
    Habitacion hab;   // Doble, capacidad 2, $1,200 por noche

    final LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void preparar() {
        service = new ReservacionService(reservacionRepository, habitacionRepository, estanciaRepository);
        hotel = Datos.hotel(1L, "TerraNova Centro", true);
        TipoHabitacion doble = Datos.tipo(1L, "Doble", 2, "1200.00");
        hab = Datos.habitacion(10L, hotel, doble, "102", EstadoHabitacion.Disponible);
    }

    private Reservacion crear(int adultos, int ninos, LocalDate entrada, LocalDate salida) {
        return service.crear(10L, "  Ana López ", "722-123-4567", "ana@ejemplo.com", "Toluca",
                entrada, salida, adultos, ninos, "IA");
    }

    private void habitacionExiste() {
        when(habitacionRepository.findById(10L)).thenReturn(Optional.of(hab));
    }

    // ------------------------------------------------------------ CREAR

    @Test
    @DisplayName("Crear: calcula noches y total, limpia el teléfono y queda Confirmada con el origen indicado")
    void crearValida() {
        habitacionExiste();
        when(reservacionRepository.save(any(Reservacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservacion r = crear(2, 0, manana, manana.plusDays(3));

        assertThat(r.getNoches()).isEqualTo(3);
        assertThat(r.getCostoTotal()).isEqualByComparingTo("3600.00");
        assertThat(r.getPrecioNoche()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(r.getTelefono()).isEqualTo("7221234567");
        assertThat(r.getNombreHuesped()).isEqualTo("Ana López");
        assertThat(r.getEstado()).isEqualTo(EstadoReservacion.Confirmada);
        assertThat(r.getOrigen()).isEqualTo("IA");
    }

    @Test
    @DisplayName("Crear: no se puede reservar si la habitación ya tiene otra reservación en esas fechas")
    void crearConTraslape() {
        habitacionExiste();
        when(reservacionRepository.contarTraslapes(eq(10L), any(), any(), eq(EstadoReservacion.Cancelada), anyLong()))
                .thenReturn(1L);

        assertThatThrownBy(() -> crear(2, 0, manana, manana.plusDays(2)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no está libre");
        verify(reservacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: adultos + niños no pueden superar la capacidad")
    void crearExcedeCapacidad() {
        habitacionExiste();

        assertThatThrownBy(() -> crear(2, 1, manana, manana.plusDays(2)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("admite máximo 2");
    }

    @Test
    @DisplayName("Crear: el teléfono debe tener 10 dígitos")
    void crearTelefonoInvalido() {
        habitacionExiste();

        assertThatThrownBy(() -> service.crear(10L, "Ana", "123", null, null, manana, manana.plusDays(1), 1, 0, "WEB"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("10 dígitos");
    }

    @Test
    @DisplayName("Crear: fechas inválidas (pasada, salida antes de entrada, más de 30 noches)")
    void crearFechasInvalidas() {
        assertThatThrownBy(() -> crear(1, 0, LocalDate.now().minusDays(1), manana))
                .hasMessageContaining("anterior a hoy");
        assertThatThrownBy(() -> crear(1, 0, manana, manana))
                .hasMessageContaining("posterior");
        assertThatThrownBy(() -> crear(1, 0, manana, manana.plusDays(31)))
                .hasMessageContaining("máxima");
    }

    @Test
    @DisplayName("Crear: no se reserva una habitación en mantenimiento ni en un hotel inactivo")
    void crearNoReservable() {
        hab.setEstado(EstadoHabitacion.Mantenimiento);
        habitacionExiste();
        assertThatThrownBy(() -> crear(1, 0, manana, manana.plusDays(1)))
                .hasMessageContaining("mantenimiento");

        hab.setEstado(EstadoHabitacion.Disponible);
        hotel.setActivo(false);
        assertThatThrownBy(() -> crear(1, 0, manana, manana.plusDays(1)))
                .hasMessageContaining("inactivo");
    }

    // ------------------------------------------------------------ MODIFICAR / CANCELAR / ELIMINAR

    private Reservacion existente(LocalDate entrada, LocalDate salida) {
        Reservacion r = new Reservacion();
        r.setIdReservacion(1L);
        r.setHabitacion(hab);
        r.setNombreHuesped("Ana López");
        r.setTelefono("7221234567");
        r.setAdultos(2);
        r.setNinos(0);
        r.setFechaEntrada(entrada);
        r.setFechaSalida(salida);
        r.setNoches((int) java.time.temporal.ChronoUnit.DAYS.between(entrada, salida));
        r.setPrecioNoche(new BigDecimal("1200.00"));
        r.setCostoTotal(new BigDecimal("2400.00"));
        r.setEstado(EstadoReservacion.Confirmada);
        return r;
    }

    @Test
    @DisplayName("Modificar: se puede corregir el nombre de una reservación que ya empezó (sin cambiar sus fechas)")
    void modificarEnCurso() {
        Reservacion r = existente(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        when(reservacionRepository.findByIdConDetalle(1L)).thenReturn(Optional.of(r));
        when(reservacionRepository.save(any(Reservacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservacion m = service.modificar(1L, null, "Ana María López", null, null, null, null, null, null, null);

        assertThat(m.getNombreHuesped()).isEqualTo("Ana María López");
        assertThat(m.getNoches()).isEqualTo(2);
    }

    @Test
    @DisplayName("Modificar: al cambiar fechas se recalcula el total")
    void modificarFechasRecalculaTotal() {
        Reservacion r = existente(manana, manana.plusDays(2));
        when(reservacionRepository.findByIdConDetalle(1L)).thenReturn(Optional.of(r));
        when(reservacionRepository.save(any(Reservacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservacion m = service.modificar(1L, null, null, null, null, null, manana, manana.plusDays(5), null, null);

        assertThat(m.getNoches()).isEqualTo(5);
        assertThat(m.getCostoTotal()).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Modificar: una reservación cancelada ya no se puede modificar")
    void modificarCancelada() {
        Reservacion r = existente(manana, manana.plusDays(2));
        r.setEstado(EstadoReservacion.Cancelada);
        when(reservacionRepository.findByIdConDetalle(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.modificar(1L, null, "Otro", null, null, null, null, null, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("cancelada");
    }

    @Test
    @DisplayName("Cancelar: queda en el historial como Cancelada y no se puede cancelar dos veces")
    void cancelar() {
        Reservacion r = existente(manana, manana.plusDays(2));
        when(reservacionRepository.findByIdConDetalle(1L)).thenReturn(Optional.of(r));
        when(reservacionRepository.save(r)).thenReturn(r);

        assertThat(service.cancelar(1L).getEstado()).isEqualTo(EstadoReservacion.Cancelada);
        assertThatThrownBy(() -> service.cancelar(1L)).hasMessageContaining("ya estaba cancelada");
    }

    @Test
    @DisplayName("Eliminar: borra el registro")
    void eliminar() {
        Reservacion r = existente(manana, manana.plusDays(2));
        when(reservacionRepository.findByIdConDetalle(1L)).thenReturn(Optional.of(r));

        service.eliminar(1L);

        verify(reservacionRepository).delete(r);
    }

    @Test
    @DisplayName("Buscar un folio inexistente avisa con un mensaje claro")
    void buscarInexistente() {
        when(reservacionRepository.findByIdConDetalle(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("#99");
    }
}
