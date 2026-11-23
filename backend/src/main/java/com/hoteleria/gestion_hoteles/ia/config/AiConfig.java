package com.hoteleria.gestion_hoteles.ia.config;

import com.hoteleria.gestion_hoteles.ia.tools.AvailabilityTool;
import com.hoteleria.gestion_hoteles.ia.tools.ExecutiveStatsTool;
import com.hoteleria.gestion_hoteles.ia.tools.GerenteInformeTool;
import com.hoteleria.gestion_hoteles.ia.tools.HotelInfoTool;
import com.hoteleria.gestion_hoteles.ia.tools.ReservationTool;
import com.hoteleria.gestion_hoteles.ia.tools.RoomInfoTool;
import com.hoteleria.gestion_hoteles.ia.tools.StayInfoTool;
import com.hoteleria.gestion_hoteles.ia.tools.UsuarioReservationTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un ChatClient por proveedor, ambos con las mismas Tools y la misma memoria de chat (MessageChatMemoryAdvisor).
 * Las Tools de consulta son de solo lectura; ReservationTool puede escribir, pero solo con confirmación del usuario.
 * El prompt de sistema se arma en HotelAssistantService (incluye la fecha actual).
 */
@Configuration
public class AiConfig {

    @Bean("ollamaClient")
    public ChatClient ollamaClient(OllamaChatModel chatModel, ChatMemory chatMemory,
                                   HotelInfoTool hotelInfoTool, RoomInfoTool roomInfoTool,
                                   AvailabilityTool availabilityTool, StayInfoTool stayInfoTool,
                                   ExecutiveStatsTool executiveStatsTool, ReservationTool reservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, roomInfoTool, availabilityTool, stayInfoTool, executiveStatsTool, reservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("geminiClient")
    public ChatClient geminiClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory,
                                   HotelInfoTool hotelInfoTool, RoomInfoTool roomInfoTool,
                                   AvailabilityTool availabilityTool, StayInfoTool stayInfoTool,
                                   ExecutiveStatsTool executiveStatsTool, ReservationTool reservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, roomInfoTool, availabilityTool, stayInfoTool, executiveStatsTool, reservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // ------------------------------------------------------------------
    // Clientes para el rol USUARIO: SOLO información pública (hoteles y disponibilidad libre) y sus propias
    // reservaciones. No reciben RoomInfoTool (ocupadas/mantenimiento), StayInfoTool ni ExecutiveStatsTool.
    // ------------------------------------------------------------------

    @Bean("ollamaUsuarioClient")
    public ChatClient ollamaUsuarioClient(OllamaChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                          AvailabilityTool availabilityTool, UsuarioReservationTool usuarioReservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, availabilityTool, usuarioReservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("geminiUsuarioClient")
    public ChatClient geminiUsuarioClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                          AvailabilityTool availabilityTool, UsuarioReservationTool usuarioReservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, availabilityTool, usuarioReservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // ------------------------------------------------------------------
    // Fase 6 · GERENTE: solo lectura de datos crudos (hoteles, dashboard ejecutivo e informes gerenciales).
    // Sin herramientas de reservación ni búsqueda de habitaciones para reservar.
    // RECEPCIONISTA: operación de mostrador (hoteles, habitaciones, disponibilidad, estancias y reservaciones).
    // ------------------------------------------------------------------

    @Bean("ollamaGerenteClient")
    public ChatClient ollamaGerenteClient(OllamaChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                          ExecutiveStatsTool executiveStatsTool, GerenteInformeTool gerenteInformeTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, executiveStatsTool, gerenteInformeTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("geminiGerenteClient")
    public ChatClient geminiGerenteClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                          ExecutiveStatsTool executiveStatsTool, GerenteInformeTool gerenteInformeTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, executiveStatsTool, gerenteInformeTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("ollamaRecepcionClient")
    public ChatClient ollamaRecepcionClient(OllamaChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                            RoomInfoTool roomInfoTool, AvailabilityTool availabilityTool,
                                            StayInfoTool stayInfoTool, ReservationTool reservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, roomInfoTool, availabilityTool, stayInfoTool, reservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("geminiRecepcionClient")
    public ChatClient geminiRecepcionClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory, HotelInfoTool hotelInfoTool,
                                            RoomInfoTool roomInfoTool, AvailabilityTool availabilityTool,
                                            StayInfoTool stayInfoTool, ReservationTool reservationTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(hotelInfoTool, roomInfoTool, availabilityTool, stayInfoTool, reservationTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
