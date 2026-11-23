package com.hoteleria.gestion_hoteles.ia.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Confirmación OBLIGATORIA para que la IA cree, modifique, cancele o elimine reservaciones.
 *
 * Funciona sin depender de que el modelo "se acuerde" de volver a llamar la herramienta (los modelos pequeños
 * como llama3.2 fallan ahí): la herramienta solo VALIDA y deja la acción como pendiente de la conversación.
 * El servicio muestra el resumen al usuario y, cuando éste responde «sí» en su siguiente mensaje, ejecuta la
 * acción directamente en Java. Si responde «no» (o cambia de tema) la acción se descarta.
 */
@Component
public class ConfirmacionAcciones {

    /** Acción pendiente: qué herramienta la pidió, el resumen para el usuario y el código que la ejecuta. */
    public record Pendiente(String herramienta, String accion, String resumen, Supplier<String> ejecutar, Instant creada) {
    }

    private static final Duration VIGENCIA = Duration.ofMinutes(15);
    private static final String CONVERSACION_POR_DEFECTO = "1";

    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();
    private final Map<String, String> ultimosResultados = new ConcurrentHashMap<>();
    /** Folio de la última reservación creada en cada chat (para consultarla sin pedir el folio de nuevo). */
    private final Map<String, Long> ultimosFolios = new ConcurrentHashMap<>();
    private final ThreadLocal<String> conversacionActual = new ThreadLocal<>();

    // ------------------------------------------------------------------ ciclo de la petición

    public void iniciar(String conversationId) {
        conversacionActual.set(conversationId);
    }

    public void finalizar() {
        conversacionActual.remove();
    }

    // ------------------------------------------------------------------ acciones pendientes

    /** Las herramientas llaman aquí en lugar de escribir en la base de datos. Reemplaza cualquier pendiente anterior. */
    public void solicitar(String herramienta, String accion, String resumen, Supplier<String> ejecutar) {
        String conversacion = conversacionActual.get() == null ? CONVERSACION_POR_DEFECTO : conversacionActual.get();
        pendientes.put(conversacion, new Pendiente(herramienta, accion, resumen, ejecutar, Instant.now()));
    }

    /** Pendiente vigente de la conversación (sin consumirlo). */
    public Optional<Pendiente> pendiente(String conversationId) {
        Pendiente p = pendientes.get(conversationId);
        if (p == null) {
            return Optional.empty();
        }
        if (p.creada().plus(VIGENCIA).isBefore(Instant.now())) {
            pendientes.remove(conversationId);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    /** Devuelve el pendiente y lo quita: una acción confirmada se ejecuta una sola vez. */
    public Optional<Pendiente> tomar(String conversationId) {
        Optional<Pendiente> p = pendiente(conversationId);
        p.ifPresent(x -> pendientes.remove(conversationId, x));
        return p;
    }

    public void descartar(String conversationId) {
        pendientes.remove(conversationId);
    }

    /** Lo último que se ejecutó en la conversación: se le pasa al modelo para que no pierda el hilo. */
    public void recordarResultado(String conversationId, String texto) {
        ultimosResultados.put(conversationId, texto);
    }

    public Optional<String> ultimoResultado(String conversationId) {
        return Optional.ofNullable(ultimosResultados.get(conversationId));
    }

    /** Chat de la petición en curso (solo válido dentro de una herramienta, antes de finalizar()). */
    public String conversacionActual() {
        return conversacionActual.get() == null ? CONVERSACION_POR_DEFECTO : conversacionActual.get();
    }

    public void recordarFolio(String conversationId, Long folio) {
        if (conversationId != null && folio != null) {
            ultimosFolios.put(conversationId, folio);
        }
    }

    public Optional<Long> ultimoFolio(String conversationId) {
        return Optional.ofNullable(ultimosFolios.get(conversationId));
    }

    public void olvidar(String conversationId) {
        pendientes.remove(conversationId);
        ultimosResultados.remove(conversationId);
        ultimosFolios.remove(conversationId);
    }

    // ------------------------------------------------------------------ interpretar la respuesta del usuario

    private static final Set<String> NUCLEO_SI = Set.of("si", "sii", "sip", "simon", "confirmo", "confirmar", "confirmado",
            "confirmada", "claro", "ok", "okay", "dale", "adelante", "correcto", "correcta", "procede", "proceder",
            "hazlo", "listo", "vale", "perfecto", "exacto", "afirmativo", "yes", "acuerdo");

    private static final Set<String> RELLENO_SI = Set.of("de", "por", "favor", "gracias", "lo", "la", "eso", "esta", "bien",
            "asi", "todo", "que", "se", "haz", "es", "muy", "quiero", "reservacion", "reserva", "el", "los", "cambios",
            "cambio", "elimina", "eliminar", "cancela", "cancelar", "cancelala", "eliminala", "procedo");

    private static final Set<String> NO = Set.of("no", "nel", "nunca", "nope", "negativo", "descarta", "descartar",
            "olvidalo", "detente", "espera", "mejor");

    private static final Set<String> CANCELA = Set.of("cancela", "cancelar", "cancelalo", "cancelame");

    /** «sí», «sí, confirmo», «ok, adelante», «dale»... (todo el mensaje debe ser una afirmación corta). */
    public static boolean esAfirmativo(String texto) {
        List<String> palabras = palabras(texto);
        if (palabras.isEmpty() || palabras.size() > 8) {
            return false;
        }
        if (palabras.stream().anyMatch(NO::contains)) {
            return false;
        }
        boolean hayNucleo = palabras.stream().anyMatch(NUCLEO_SI::contains);
        boolean soloAfirmacion = palabras.stream().allMatch(p -> NUCLEO_SI.contains(p) || RELLENO_SI.contains(p));
        return hayNucleo && soloAfirmacion;
    }

    /**
     * «no», «mejor no», «descártalo»... Si la acción pendiente NO es una cancelación/eliminación, «cancela» también
     * cuenta como rechazo (el usuario quiere detener lo propuesto).
     */
    public static boolean esNegativo(String texto, String accionPendiente) {
        List<String> palabras = palabras(texto);
        if (palabras.isEmpty() || palabras.size() > 8) {
            return false;
        }
        if (palabras.stream().anyMatch(NO::contains)) {
            return true;
        }
        boolean accionDestructiva = "cancelar".equals(accionPendiente) || "eliminar".equals(accionPendiente);
        return !accionDestructiva && palabras.stream().anyMatch(CANCELA::contains) && !esAfirmativo(texto);
    }

    private static List<String> palabras(String texto) {
        if (texto == null) {
            return List.of();
        }
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9ñ ]", " ")
                .trim();
        if (limpio.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(limpio.split("\\s+"));
    }
}
