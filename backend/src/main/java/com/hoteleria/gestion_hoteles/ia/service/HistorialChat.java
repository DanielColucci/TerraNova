package com.hoteleria.gestion_hoteles.ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lista de chats y transcripción de cada uno (lo que se ve en pantalla), guardada en MySQL.
 * Es independiente de la memoria que usa el modelo (SPRING_AI_CHAT_MEMORY): aquí no hay límite de 20 mensajes
 * y se guardan el modelo usado, las herramientas y el tiempo de cada respuesta.
 * Las tablas se crean solas la primera vez (CREATE TABLE IF NOT EXISTS), así que no hay que ejecutar SQL a mano.
 */
@Component
public class HistorialChat {

    private static final Logger log = LoggerFactory.getLogger(HistorialChat.class);

    public static final int MAX_TITULO = 120;

    public record Conversacion(String id, String titulo, String actualizada) {
    }

    public record Mensaje(Long id, String rol, String texto, String modelo, String herramientas, Long tiempoMs) {
    }

    private final JdbcTemplate jdbc;
    private volatile boolean listo = false;

    public HistorialChat(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void asegurarTablas() {
        if (listo) {
            return;
        }
        synchronized (this) {
            if (listo) {
                return;
            }
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS chat_conversacion (
                        id          VARCHAR(36)  NOT NULL,
                        titulo      VARCHAR(120) NOT NULL,
                        creada      DATETIME(6)  NOT NULL,
                        actualizada DATETIME(6)  NOT NULL,
                        PRIMARY KEY (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS chat_mensaje (
                        id              BIGINT      NOT NULL AUTO_INCREMENT,
                        conversation_id VARCHAR(36) NOT NULL,
                        rol             VARCHAR(10) NOT NULL,
                        texto           MEDIUMTEXT  NOT NULL,
                        modelo          VARCHAR(20) NULL,
                        herramientas    VARCHAR(300) NULL,
                        tiempo_ms       BIGINT      NULL,
                        creado          DATETIME(6) NOT NULL,
                        PRIMARY KEY (id),
                        INDEX idx_chat_mensaje_conv (conversation_id, id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            listo = true;
            log.info("[CHAT] tablas chat_conversacion y chat_mensaje listas");
        }
    }

    public static String nuevoId() {
        return "c" + UUID.randomUUID().toString().replace("-", "");   // 33 caracteres (la memoria admite 36)
    }

    public List<Conversacion> listar() {
        asegurarTablas();
        return jdbc.query("SELECT id, titulo, actualizada FROM chat_conversacion ORDER BY actualizada DESC LIMIT 100",
                (rs, n) -> new Conversacion(rs.getString("id"), rs.getString("titulo"),
                        rs.getTimestamp("actualizada").toLocalDateTime().toString()));
    }

    public boolean existe(String id) {
        asegurarTablas();
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversacion WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public Conversacion crear(String titulo) {
        return crear(titulo, "c");
    }

    /** prefijo: «c» para el administrador; «u{idUsuario}_» para un USUARIO (así cada cuenta solo ve sus chats). */
    public Conversacion crear(String titulo, String prefijo) {
        asegurarTablas();
        String id = (prefijo + UUID.randomUUID().toString().replace("-", ""));
        id = id.length() > 36 ? id.substring(0, 36) : id;
        LocalDateTime ahora = LocalDateTime.now();
        jdbc.update("INSERT INTO chat_conversacion (id, titulo, creada, actualizada) VALUES (?, ?, ?, ?)",
                id, recortar(titulo), Timestamp.valueOf(ahora), Timestamp.valueOf(ahora));
        return new Conversacion(id, recortar(titulo), ahora.toString());
    }

    /** Crea la conversación si todavía no existe (por ejemplo, al usar el endpoint de texto plano con un id propio). */
    public void asegurarConversacion(String id, String titulo) {
        if (existe(id)) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        jdbc.update("INSERT INTO chat_conversacion (id, titulo, creada, actualizada) VALUES (?, ?, ?, ?)",
                id, recortar(titulo), Timestamp.valueOf(ahora), Timestamp.valueOf(ahora));
    }

    public void agregar(String id, String rol, String texto, String modelo, List<String> herramientas, Long tiempoMs) {
        asegurarTablas();
        LocalDateTime ahora = LocalDateTime.now();
        String h = herramientas == null || herramientas.isEmpty() ? null : recortar(String.join(", ", herramientas), 300);
        jdbc.update("INSERT INTO chat_mensaje (conversation_id, rol, texto, modelo, herramientas, tiempo_ms, creado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, rol, texto, modelo, h, tiempoMs, Timestamp.valueOf(ahora));
        jdbc.update("UPDATE chat_conversacion SET actualizada = ? WHERE id = ?", Timestamp.valueOf(ahora), id);
    }

    public List<Mensaje> mensajes(String id) {
        asegurarTablas();
        return jdbc.query("SELECT id, rol, texto, modelo, herramientas, tiempo_ms FROM chat_mensaje "
                        + "WHERE conversation_id = ? ORDER BY id",
                (rs, n) -> new Mensaje(rs.getLong("id"), rs.getString("rol"), rs.getString("texto"),
                        rs.getString("modelo"), rs.getString("herramientas"),
                        rs.getObject("tiempo_ms") == null ? null : rs.getLong("tiempo_ms")), id);
    }

    public void renombrar(String id, String titulo) {
        asegurarTablas();
        jdbc.update("UPDATE chat_conversacion SET titulo = ? WHERE id = ?", recortar(titulo), id);
    }

    /** Si el chat todavía se llama «Nueva conversación», toma como título la primera pregunta. */
    public void tituloAutomatico(String id, String primeraPregunta) {
        asegurarTablas();
        jdbc.update("UPDATE chat_conversacion SET titulo = ? WHERE id = ? AND titulo = 'Nueva conversación'",
                recortar(primeraPregunta, 60), id);
    }

    public void eliminar(String id) {
        asegurarTablas();
        jdbc.update("DELETE FROM chat_mensaje WHERE conversation_id = ?", id);
        jdbc.update("DELETE FROM chat_conversacion WHERE id = ?", id);
    }

    private static String recortar(String texto) {
        return recortar(texto, MAX_TITULO);
    }

    private static String recortar(String texto, int max) {
        String t = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            t = "Nueva conversación";
        }
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
