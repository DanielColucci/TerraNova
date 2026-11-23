package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.dto.CheckInForm;
import com.hoteleria.gestion_hoteles.dto.CheckOutForm;
import com.hoteleria.gestion_hoteles.dto.HabitacionForm;
import com.hoteleria.gestion_hoteles.entity.Hotel;
import com.hoteleria.gestion_hoteles.entity.TipoHabitacion;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Validaciones (Bean Validation) de los formularios: se prueban sin levantar Spring ni la base de datos. */
@DisplayName("Validación de formularios y entidades")
class ValidacionFormulariosTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    /** Campos que tienen al menos un error. */
    private static <T> Set<String> camposConError(T objeto) {
        return validator.validate(objeto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------ CHECK-IN

    private CheckInForm checkInValido() {
        CheckInForm f = new CheckInForm();
        f.setIdHabitacion(1L);
        f.setHuesped("Mariana López");
        f.setIdentificacion("INE-123");
        f.setNumHuespedes(2);
        f.setFechaSalidaPrevista(LocalDate.now().plusDays(1));
        return f;
    }

    @Test
    @DisplayName("Check-in correcto no tiene errores")
    void checkInOk() {
        assertThat(camposConError(checkInValido())).isEmpty();
    }

    @Test
    @DisplayName("Check-in: la fecha de salida debe ser posterior a hoy")
    void checkInFechaHoy() {
        CheckInForm f = checkInValido();
        f.setFechaSalidaPrevista(LocalDate.now());
        assertThat(camposConError(f)).containsExactly("fechaSalidaPrevista");
    }

    @Test
    @DisplayName("Check-in: huésped, identificación y habitación son obligatorios")
    void checkInObligatorios() {
        CheckInForm f = checkInValido();
        f.setHuesped("   ");
        f.setIdentificacion("");
        f.setIdHabitacion(null);
        assertThat(camposConError(f)).containsExactlyInAnyOrder("huesped", "identificacion", "idHabitacion");
    }

    @Test
    @DisplayName("Check-in: número de huéspedes entre 1 y 20")
    void checkInHuespedes() {
        CheckInForm f = checkInValido();
        f.setNumHuespedes(0);
        assertThat(camposConError(f)).containsExactly("numHuespedes");
        f.setNumHuespedes(21);
        assertThat(camposConError(f)).containsExactly("numHuespedes");
    }

    // ------------------------------------------------------------ CHECK-OUT

    @Test
    @DisplayName("Check-out: cargos negativos o con más de 2 decimales no son válidos")
    void checkOutCargos() {
        CheckOutForm f = new CheckOutForm();
        f.setCargosExtra(new BigDecimal("-1"));
        assertThat(camposConError(f)).containsExactly("cargosExtra");
        f.setCargosExtra(new BigDecimal("10.123"));
        assertThat(camposConError(f)).containsExactly("cargosExtra");
        f.setCargosExtra(new BigDecimal("10.50"));
        assertThat(camposConError(f)).isEmpty();
    }

    // ------------------------------------------------------------ HABITACIÓN

    @Test
    @DisplayName("Habitación: el número no admite espacios ni símbolos; el piso va de 0 a 100")
    void habitacionFormato() {
        HabitacionForm f = new HabitacionForm();
        f.setIdHotel(1L);
        f.setIdTipo(1L);
        f.setNumero("10 1");
        f.setPiso(-1);
        assertThat(camposConError(f)).containsExactlyInAnyOrder("numero", "piso");

        f.setNumero("A-101");
        f.setPiso(0);
        assertThat(camposConError(f)).isEmpty();
    }

    // ------------------------------------------------------------ HOTEL

    @Test
    @DisplayName("Hotel: correo y teléfono con formato incorrecto")
    void hotelContacto() {
        Hotel h = Datos.hotel(null, "Hotel Prueba", true);
        assertThat(camposConError(h)).isEmpty();

        h.setCorreo("sin-arroba.com");
        h.setTelefono("abc");
        assertThat(camposConError(h)).containsExactlyInAnyOrder("correo", "telefono");
    }

    @Test
    @DisplayName("Hotel: los campos obligatorios no pueden ir en blanco")
    void hotelObligatorios() {
        Hotel h = Datos.hotel(null, " ", true);
        h.setCiudad("");
        assertThat(camposConError(h)).containsExactlyInAnyOrder("nombre", "ciudad");
    }

    // ------------------------------------------------------------ TIPO DE HABITACIÓN

    @Test
    @DisplayName("Tipo: capacidad entre 1 y 20 y precio no negativo")
    void tipoNumeros() {
        TipoHabitacion t = Datos.tipo(null, "Doble", 0, "-5");
        assertThat(camposConError(t)).containsExactlyInAnyOrder("capacidad", "precioBase");

        t.setCapacidad(21);
        t.setPrecioBase(new BigDecimal("900"));
        assertThat(camposConError(t)).containsExactly("capacidad");

        t.setCapacidad(2);
        assertThat(camposConError(t)).isEmpty();
    }
}
