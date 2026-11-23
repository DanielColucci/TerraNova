package com.hoteleria.gestion_hoteles.ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deja constancia de cada herramienta (Tool) que ejecuta la IA.
 * Una respuesta coherente por sí sola no demuestra que se consultó la base de datos:
 * aquí queda en la consola ("[TOOL] ...") y se devuelve en la respuesta JSON y en la pantalla del asistente.
 */
@Component
public class ToolAuditoria {

    private static final Logger log = LoggerFactory.getLogger("ia.tools");

    private final ThreadLocal<List<String>> actual = new ThreadLocal<>();

    public void iniciar() {
        actual.set(new ArrayList<>());
    }

    public void registrar(String herramienta, String argumentos) {
        log.info("[TOOL] {}({})", herramienta, argumentos);
        List<String> lista = actual.get();
        if (lista != null && !lista.contains(herramienta)) {
            lista.add(herramienta);
        }
    }

    public List<String> finalizar() {
        List<String> lista = actual.get();
        actual.remove();
        return lista == null ? List.of() : List.copyOf(lista);
    }
}
