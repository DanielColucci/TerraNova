package com.hoteleria.gestion_hoteles.ia.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Primer filtro (antes de gastar tokens del modelo): rechaza peticiones de código, intentos de manipular al
 * asistente y preguntas ajenas al hotel. Es una lista básica; la segunda barrera son las reglas del prompt de sistema.
 */
@Component
public class GuardiaConsultas {

    public static final String MENSAJE_FUERA_DE_TEMA =
            "Solo puedo ayudarte con información y reservaciones de los hoteles TerraNova: hoteles, tipos de "
                    + "habitación, disponibilidad, tarifas y reservaciones. ¿Qué te gustaría consultar o reservar?";

    private static final Pattern CODIGO = Pattern.compile(
            "\\b(html|css|python|javascript|typescript|java|kotlin|php|sql|json|xml|yaml|bash|powershell|c\\+\\+|c#|"
                    + "react|angular|node|script|algoritmo|regex|api rest|programacion|codigo fuente)\\b"
                    + "|(escribe|genera|crea|dame|haz|programa)(me)?\\s+(un\\s+|el\\s+)?(codigo|programa|funcion|clase|pagina web|app)\\b");

    private static final Pattern MANIPULACION = Pattern.compile(
            "(ignora|olvida|omite|desactiva|salta)\\s+(todas?\\s+)?(tus|las|todas las|todo lo)\\s+(instrucciones|reglas|restricciones|anteriores)"
                    + "|prompt\\s+(del\\s+)?sistema|system\\s*prompt|instrucciones\\s+(internas|del\\s+sistema|ocultas)"
                    + "|modo\\s+(desarrollador|dios|sin\\s+restricciones)|developer\\s+mode|jailbreak"
                    + "|actua\\s+como|finge\\s+(ser|que)|haz\\s+de\\s+cuenta|ahora\\s+eres|pretend\\s+to\\s+be"
                    + "|revela\\s+(tu|tus)|muestrame\\s+(tu|tus)\\s+(prompt|instrucciones|configuracion)");

    private static final Pattern AJENO = Pattern.compile(
            "\\b(presidente|presidenta|trump|biden|obama|sheinbaum|putin|elecciones|politic[ao]s?|gobernador|"
                    + "receta|chiste|poema|cancion|letra de|horoscopo|bitcoin|criptomoneda|acciones de bolsa|"
                    + "futbol|mundial|película|pelicula|serie de netflix|tarea de|ensayo sobre|"
                    + "capital de|quien (gano|fue)|cuanto es \\d|resuelve|traduce)\\b");

    /** Devuelve el mensaje de rechazo, o vacío si la pregunta puede pasar al modelo. */
    public Optional<String> evaluar(String pregunta) {
        String n = normalizar(pregunta);
        if (CODIGO.matcher(n).find()) {
            return Optional.of("No puedo generar código ni entregar información en formatos de programación (HTML, Python, "
                    + "JSON, SQL, etc.). " + MENSAJE_FUERA_DE_TEMA);
        }
        if (MANIPULACION.matcher(n).find()) {
            return Optional.of("No puedo cambiar mis reglas ni revelar mi configuración interna. " + MENSAJE_FUERA_DE_TEMA);
        }
        if (AJENO.matcher(n).find()) {
            return Optional.of(MENSAJE_FUERA_DE_TEMA);
        }
        return Optional.empty();
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
