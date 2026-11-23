package com.hoteleria.gestion_hoteles.ia.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Memoria de conversación del asistente. Guarda los mensajes en MySQL (tabla SPRING_AI_CHAT_MEMORY) mediante el
 * ChatMemoryRepository JDBC de Spring AI, así que sobrevive al reinicio de la aplicación.
 * Ventana de 20 mensajes (10 preguntas + 10 respuestas): suficiente para reunir los datos de una reservación
 * sin enviar historiales enormes al modelo (cada mensaje consume tokens).
 */
@Configuration
public class MemoryConfig {

    public static final int MAX_MENSAJES = 20;

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MENSAJES)
                .build();
    }
}
