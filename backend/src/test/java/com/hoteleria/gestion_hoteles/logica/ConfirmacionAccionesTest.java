package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.ia.service.ConfirmacionAcciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La IA solo escribe en la base de datos después de un «sí» explícito del usuario. */
@DisplayName("IA: confirmación de acciones sobre reservaciones")
class ConfirmacionAccionesTest {

    ConfirmacionAcciones confirmaciones;

    @BeforeEach
    void preparar() {
        confirmaciones = new ConfirmacionAcciones();
    }

    @Test
    @DisplayName("Respuestas afirmativas cortas")
    void afirmativos() {
        for (String t : new String[]{"sí", "Sí, confirmo", "ok", "Dale", "sí por favor", "Sí, confirmo.", "adelante", "de acuerdo"}) {
            assertThat(ConfirmacionAcciones.esAfirmativo(t)).as(t).isTrue();
        }
    }

    @Test
    @DisplayName("Un mensaje que agrega otra cosa NO cuenta como confirmación")
    void noAfirmativos() {
        for (String t : new String[]{"", "no", "sí pero cambia la fecha", "quiero otra habitación", "sí, no",
                "confirmo que me llamo Juan", "¿cuánto cuesta?"}) {
            assertThat(ConfirmacionAcciones.esAfirmativo(t)).as(t).isFalse();
        }
    }

    @Test
    @DisplayName("Rechazos: «no», «mejor no», «No, descartar»")
    void negativos() {
        for (String t : new String[]{"no", "mejor no", "No, descartar", "nel", "olvídalo"}) {
            assertThat(ConfirmacionAcciones.esNegativo(t, "crear")).as(t).isTrue();
        }
    }

    @Test
    @DisplayName("«cancela» rechaza una creación, pero confirma una cancelación")
    void cancelaSegunLaAccion() {
        assertThat(ConfirmacionAcciones.esNegativo("cancela", "crear")).isTrue();
        assertThat(ConfirmacionAcciones.esNegativo("cancela", "cancelar")).isFalse();
        assertThat(ConfirmacionAcciones.esAfirmativo("sí, cancélala")).isTrue();
    }

    @Test
    @DisplayName("La acción queda pendiente, no se ejecuta sola y se consume al confirmarla (una sola vez)")
    void ciclo() {
        int[] ejecuciones = {0};
        confirmaciones.iniciar("c1");
        confirmaciones.solicitar("crearReservacion", "crear", "resumen", () -> {
            ejecuciones[0]++;
            return "hecho";
        });
        confirmaciones.finalizar();

        assertThat(ejecuciones[0]).isZero();                                   // solo validó
        assertThat(confirmaciones.pendiente("c1")).isPresent();
        assertThat(confirmaciones.pendiente("c2")).isEmpty();                  // otro chat no la ve

        String resultado = confirmaciones.tomar("c1").orElseThrow().ejecutar().get();
        assertThat(resultado).isEqualTo("hecho");
        assertThat(ejecuciones[0]).isEqualTo(1);
        assertThat(confirmaciones.tomar("c1")).isEmpty();                      // no se puede repetir
    }

    @Test
    @DisplayName("Una nueva solicitud reemplaza a la anterior y descartar la elimina")
    void reemplazarYDescartar() {
        confirmaciones.iniciar("c1");
        confirmaciones.solicitar("crearReservacion", "crear", "primera", () -> "1");
        confirmaciones.solicitar("cancelarReservacion", "cancelar", "segunda", () -> "2");
        confirmaciones.finalizar();

        assertThat(confirmaciones.pendiente("c1").orElseThrow().resumen()).isEqualTo("segunda");
        confirmaciones.descartar("c1");
        assertThat(confirmaciones.pendiente("c1")).isEmpty();
    }

    @Test
    @DisplayName("Se recuerda el último resultado para darle contexto al modelo y se borra con el chat")
    void ultimoResultado() {
        confirmaciones.recordarResultado("c1", "Reservación #7 creada");
        assertThat(confirmaciones.ultimoResultado("c1")).contains("Reservación #7 creada");
        confirmaciones.olvidar("c1");
        assertThat(confirmaciones.ultimoResultado("c1")).isEmpty();
    }
}
