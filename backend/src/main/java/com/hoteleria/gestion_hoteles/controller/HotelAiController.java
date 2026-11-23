package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.ia.service.HistorialChat.Conversacion;
import com.hoteleria.gestion_hoteles.ia.service.HistorialChat.Mensaje;
import com.hoteleria.gestion_hoteles.ia.service.HotelAssistantService;
import com.hoteleria.gestion_hoteles.ia.service.RespuestaIA;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versión JSON del asistente (la usa la pantalla /asistente):
 *   POST   /api/hotel-ai/ask?model=ollama&conversationId=...   {"pregunta": "..."}
 *   GET    /api/hotel-ai/chats                  lista de chats
 *   POST   /api/hotel-ai/chats                  crea un chat nuevo
 *   GET    /api/hotel-ai/chats/{id}             mensajes del chat
 *   PUT    /api/hotel-ai/chats/{id}             renombra {"titulo": "..."}
 *   DELETE /api/hotel-ai/chats/{id}             borra el chat y su memoria
 */
@RestController
@RequestMapping("/api/hotel-ai")
public class HotelAiController {

    public record HotelQuestion(String pregunta) {
    }

    public record NuevoTitulo(String titulo) {
    }

    private final HotelAssistantService assistantService;

    public HotelAiController(HotelAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    public RespuestaIA preguntar(@RequestBody HotelQuestion request,
                                 @RequestParam(defaultValue = "gemini") String model,
                                 @RequestParam(required = false) String conversationId) {
        return assistantService.preguntar(request.pregunta(), model, conversationId);
    }

    @GetMapping("/chats")
    public List<Conversacion> chats() {
        return assistantService.listarChats();
    }

    @PostMapping("/chats")
    public Conversacion nuevoChat() {
        return assistantService.nuevoChat();
    }

    @GetMapping("/chats/{id}")
    public Map<String, Object> detalle(@PathVariable String id) {
        List<Mensaje> mensajes = assistantService.mensajesDe(id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensajes", mensajes);
        r.put("pendiente", assistantService.hayAccionPendiente(id));
        return r;
    }

    @PutMapping("/chats/{id}")
    public void renombrar(@PathVariable String id, @RequestBody NuevoTitulo cuerpo) {
        assistantService.renombrarChat(id, cuerpo.titulo());
    }

    @DeleteMapping("/chats/{id}")
    public void eliminar(@PathVariable String id) {
        assistantService.olvidar(id);
    }

    /** Compatibilidad con la versión anterior: borra el chat indicado. */
    @DeleteMapping("/memory")
    public void olvidar(@RequestParam(required = false) String conversationId) {
        assistantService.olvidar(conversationId);
    }
}
