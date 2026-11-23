package com.hoteleria.gestion_hoteles.ia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * DTO de solo lectura que reciben los modelos de IA desde las Tools.
 * Solo llevan los campos necesarios: NO incluyen datos personales de huéspedes
 * (nombre, identificación, etc.).
 */
public final class IaDtos {

    private IaDtos() {
    }

    private static String pesos(BigDecimal v) {
        return v == null ? "sin precio" : "$" + String.format(Locale.US, "%,.2f", v);
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? "sin dato" : v.trim();
    }

    private static String personas(int n) {
        return n + (n == 1 ? " persona" : " personas");
    }

    private static String act(boolean activo) {
        return activo ? "activo" : "inactivo";
    }

    /*
     * Cada registro lleva «ficha»: una línea con TODOS sus datos ya redactados. Los modelos pequeños suelen
     * omitir algún campo al listar (p. ej. la capacidad); con la ficha basta con copiarla completa.
     * Se calcula en el constructor corto, así los llamadores no cambian.
     */
    public record HotelInfo(String ficha, Long id, String nombre, String direccion, String ciudad,
                            String telefono, String correo, boolean activo, int totalHabitaciones) {
        public HotelInfo(Long id, String nombre, String direccion, String ciudad,
                         String telefono, String correo, boolean activo, int totalHabitaciones) {
            this(nombre + " | ciudad: " + texto(ciudad) + " | dirección: " + texto(direccion)
                    + " | teléfono: " + texto(telefono) + " | correo: " + texto(correo)
                    + " | estado: " + act(activo) + " | habitaciones: " + totalHabitaciones,
                    id, nombre, direccion, ciudad, telefono, correo, activo, totalHabitaciones);
        }
    }

    public record TipoInfo(String ficha, Long id, String nombre, int capacidad, String descripcion,
                           BigDecimal precioBase, int habitacionesQueLoUsan) {
        public TipoInfo(Long id, String nombre, int capacidad, String descripcion,
                        BigDecimal precioBase, int habitacionesQueLoUsan) {
            this(nombre + " | capacidad: " + personas(capacidad) + " | precio base: " + pesos(precioBase)
                    + " por noche | descripción: " + texto(descripcion)
                    + " | habitaciones que lo usan: " + habitacionesQueLoUsan,
                    id, nombre, capacidad, descripcion, precioBase, habitacionesQueLoUsan);
        }
    }

    public record HabitacionInfo(String ficha, Long id, String hotel, boolean hotelActivo, String numero, int piso,
                                 String tipo, int capacidad, BigDecimal precioBase, String estado) {
        public HabitacionInfo(Long id, String hotel, boolean hotelActivo, String numero, int piso,
                              String tipo, int capacidad, BigDecimal precioBase, String estado) {
            this(hotel + " (" + act(hotelActivo) + ") | habitación " + numero + " | piso: " + piso
                    + " | tipo: " + tipo + " | capacidad: " + personas(capacidad)
                    + " | precio base: " + pesos(precioBase) + " por noche | estado: " + estado,
                    id, hotel, hotelActivo, numero, piso, tipo, capacidad, precioBase, estado);
        }
    }

    public record ConteoHotel(String hotel, String ciudad, boolean hotelActivo, long total,
                              long disponibles, long ocupadas, long mantenimiento) {
    }

    public record Disponible(String ficha, String hotel, String ciudad, String habitacion, int piso, String tipo,
                             int capacidad, BigDecimal precioPorNoche, BigDecimal totalEstimado) {
        public Disponible(String hotel, String ciudad, String habitacion, int piso, String tipo,
                          int capacidad, BigDecimal precioPorNoche, BigDecimal totalEstimado) {
            this(hotel + " (" + texto(ciudad) + ") | habitación " + habitacion + " | piso: " + piso
                    + " | tipo: " + tipo + " | capacidad: " + personas(capacidad)
                    + " | precio: " + pesos(precioPorNoche) + " por noche"
                    + (totalEstimado == null ? "" : " | total estimado: " + pesos(totalEstimado)),
                    hotel, ciudad, habitacion, piso, tipo, capacidad, precioPorNoche, totalEstimado);
        }
    }

    public record EstanciaActiva(String ficha, String hotel, String habitacion, String tipo, int numHuespedes,
                                 String fechaEntrada, String salidaPrevista, boolean vencida,
                                 BigDecimal precioNoche) {
        public EstanciaActiva(String hotel, String habitacion, String tipo, int numHuespedes,
                              String fechaEntrada, String salidaPrevista, boolean vencida,
                              BigDecimal precioNoche) {
            this(hotel + " | habitación " + habitacion + " | tipo: " + tipo + " | huéspedes: " + numHuespedes
                    + " | entrada: " + fechaEntrada + " | salida prevista: " + salidaPrevista
                    + " | " + (vencida ? "salida VENCIDA" : "en plazo") + " | precio: " + pesos(precioNoche) + " por noche",
                    hotel, habitacion, tipo, numHuespedes, fechaEntrada, salidaPrevista, vencida, precioNoche);
        }
    }

    public record CheckOutReciente(String ficha, String hotel, String habitacion, String tipo, int noches,
                                   BigDecimal total, String metodoPago, String fechaSalida) {
        public CheckOutReciente(String hotel, String habitacion, String tipo, int noches,
                                BigDecimal total, String metodoPago, String fechaSalida) {
            this(hotel + " | habitación " + habitacion + " | tipo: " + tipo + " | noches: " + noches
                    + " | total: " + pesos(total) + " | pago: " + texto(metodoPago) + " | salida: " + texto(fechaSalida),
                    hotel, habitacion, tipo, noches, total, metodoPago, fechaSalida);
        }
    }

    public record EjecutivoHotel(String hotel, String ciudad, boolean activo, long total, long disponibles,
                                 long ocupadas, long mantenimiento, int pctOcupacion,
                                 BigDecimal ingresoDiario, BigDecimal costoOportunidad) {
    }

    public record EjecutivoTipo(String tipo, int capacidad, BigDecimal precioBase, long total,
                                long ocupadas, long mantenimiento, int pctOcupacion) {
    }

    public record Ejecutivo(String alcance, long totalHabitaciones, long disponibles, long ocupadas,
                            long mantenimiento, int pctOcupacion, int pctDisponible, int pctMantenimiento,
                            BigDecimal ingresoDiario, BigDecimal costoOportunidad, BigDecimal ingresoPotencial,
                            int pctCostoOportunidad, BigDecimal ingresosDelMes, long estanciasActivas,
                            List<EjecutivoHotel> porHotel, List<EjecutivoTipo> porTipo) {
    }

    /** Reservación tal como la ve la IA: sin teléfono ni correo del huésped. */
    public record ReservacionInfo(String ficha, Long folio, String hotel, String habitacion, String tipo, String huesped,
                                  String entrada, String salida, int noches, int adultos, int ninos,
                                  BigDecimal total, String estado, String registradaPor) {
        public ReservacionInfo(Long folio, String hotel, String habitacion, String tipo, String huesped,
                               String entrada, String salida, int noches, int adultos, int ninos,
                               BigDecimal total, String estado, String registradaPor) {
            this("Folio #" + folio + " | " + hotel + " | habitación " + habitacion + " (" + tipo + ")"
                    + " | huésped: " + huesped + " | del " + entrada + " al " + salida + " (" + noches + " noches)"
                    + " | adultos: " + adultos + ", niños: " + ninos + " | total: " + pesos(total)
                    + " | estado: " + estado + " | registrada por: " + registradaPor,
                    folio, hotel, habitacion, tipo, huesped, entrada, salida, noches, adultos, ninos,
                    total, estado, registradaPor);
        }
    }
}
