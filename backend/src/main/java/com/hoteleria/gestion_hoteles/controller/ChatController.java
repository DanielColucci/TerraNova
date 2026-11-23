package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.ia.service.HotelAssistantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat de texto plano, igual que en tutor-ollama-redes:
 *   POST http://localhost:8080/api/v1/chat?model=ollama   (cuerpo: la pregunta en texto, "raw")
 *   POST http://localhost:8080/api/v1/chat?model=gemini
 * Memoria: añade &conversationId=mi-sesion para separar historiales (sin él usa la conversación "1").
 * También acepta un cuerpo JSON {"pregunta": "..."} por si Postman está en modo JSON.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Pattern PREGUNTA_JSON =
            Pattern.compile("\"pregunta\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

    private final HotelAssistantService assistantService;

    public ChatController(HotelAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping(produces = "text/plain;charset=UTF-8")
    public String chat(@RequestBody String prompt,
                       @RequestParam(defaultValue = "gemini") String model,
                       @RequestParam(required = false) String conversationId) {
        return assistantService.preguntar(extraerPregunta(prompt), model, conversationId).respuesta();
    }

    /** Borra la memoria de una conversación: DELETE /api/v1/chat/memory?conversationId=mi-sesion */
    @DeleteMapping(value = "/memory", produces = "text/plain;charset=UTF-8")
    public String olvidar(@RequestParam(required = false) String conversationId) {
        assistantService.olvidar(conversationId);
        return "Memoria de la conversación borrada.";
    }

    /** Si el cuerpo es JSON con el campo "pregunta", toma ese texto; si no, usa el cuerpo tal cual. */
    private String extraerPregunta(String cuerpo) {
        if (cuerpo == null) {
            return null;
        }
        String texto = cuerpo.strip();
        if (texto.startsWith("{")) {
            Matcher m = PREGUNTA_JSON.matcher(texto);
            if (m.find()) {
                return m.group(1)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
        }
        return texto;
    }
}
