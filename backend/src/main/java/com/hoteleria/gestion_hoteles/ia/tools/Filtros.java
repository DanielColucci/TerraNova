package com.hoteleria.gestion_hoteles.ia.tools;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Utilidades compartidas por las Tools: normalización de texto, coincidencias y formato de respuesta. */
final class Filtros {

    static final Set<String> IGNORAR_HOTEL = Set.of("hotel", "hoteles", "terranova", "de", "del", "la", "el");
    static final Set<String> IGNORAR_TIPO = Set.of("habitacion", "habitaciones", "tipo", "tipos", "de", "la", "el", "una", "un");

    private static final Set<String> VALORES_VACIOS =
            Set.of("", "null", "none", "todos", "todas", "todo", "ninguno", "ninguna", "cualquiera", "n/a", "-");

    private Filtros() {
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** true si el parámetro no trae un valor útil (null, vacío, "todos", "null"...). */
    static boolean vacio(String texto) {
        return VALORES_VACIOS.contains(normalizar(texto));
    }

    /**
     * Coincidencia flexible por palabras: ignora mayúsculas, acentos y palabras genéricas.
     * "Hotel Centro" coincide con "Hotel TerraNova Centro".
     * Si la consulta solo tiene palabras genéricas (por ejemplo "Hotel TerraNova") coincide con todo.
     */
    static boolean coincide(String nombreEntidad, String consulta, Set<String> ignorar) {
        String nombre = normalizar(nombreEntidad);
        List<String> palabras = Arrays.stream(normalizar(consulta).split("[^a-z0-9]+"))
                .filter(p -> !p.isEmpty() && !ignorar.contains(p))
                .toList();
        if (palabras.isEmpty()) {
            return true;
        }
        return palabras.stream().allMatch(nombre::contains);
    }

    /** Convierte "Disponible", "disponibles", "ocupada", "mantenimiento"... al valor oficial o null si no es válido. */
    static String estadoOficial(String texto) {
        String n = normalizar(texto);
        if (n.startsWith("disponib")) {
            return "Disponible";
        }
        if (n.startsWith("ocupad")) {
            return "Ocupada";
        }
        if (n.startsWith("mantenim")) {
            return "Mantenimiento";
        }
        return null;
    }

    /** Fecha ISO (AAAA-MM-DD). Lanza IllegalArgumentException con un mensaje claro si el formato es incorrecto. */
    static LocalDate fecha(String texto, String campo) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("La " + campo + " '" + texto + "' no es válida. Usa el formato AAAA-MM-DD (por ejemplo 2026-11-10).");
        }
    }

    /** Respuesta uniforme: total, resultados y un mensaje cuando no hay datos. */
    static Map<String, Object> respuesta(List<?> resultados, String mensajeSiVacio) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", resultados.size());
        r.put("resultados", resultados);
        if (resultados.isEmpty()) {
            r.put("mensaje", mensajeSiVacio);
        }
        return r;
    }

    /** Respuesta de error controlado: el modelo debe comunicarla al usuario sin inventar datos. */
    static Map<String, Object> error(String mensaje) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", 0);
        r.put("resultados", List.of());
        r.put("error", mensaje);
        return r;
    }
}
