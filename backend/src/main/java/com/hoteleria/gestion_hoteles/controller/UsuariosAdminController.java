package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.auth.AuthService;
import com.hoteleria.gestion_hoteles.entity.Rol;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API REST de gestión de usuarios (solo ADMINISTRADOR). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuariosAdminController {

    public record CrearUsuarioRequest(String nombre, String correo, String password, Rol rol) { }
    public record CambiarRolRequest(Rol rol) { }
    public record CambiarActivoRequest(boolean activo) { }

    private final AuthService authService;

    public UsuariosAdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public Map<String, Object> lista(@RequestParam(required = false) String rol) {
        Rol filtro = null;
        if (rol != null && !rol.isBlank()) {
            try {
                filtro = Rol.valueOf(rol.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                filtro = null;
            }
        }
        List<Usuario> todos = authService.listar();
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Rol r : Rol.values()) {
            conteo.put(r.name(), todos.stream().filter(u -> u.getRol() == r).count());
        }
        final Rol f = filtro;
        var filtrados = (f == null ? todos : todos.stream().filter(u -> u.getRol() == f).toList())
                .stream().map(ApiMapper::usuario).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuarios", filtrados);
        m.put("roles", Arrays.stream(Rol.values()).map(Enum::name).toList());
        m.put("rolesPersonal", List.of(Rol.GERENTE.name(), Rol.RECEPCIONISTA.name(), Rol.ADMINISTRADOR.name()));
        m.put("conteo", conteo);
        m.put("total", todos.size());
        m.put("filtro", f != null ? f.name() : null);
        return m;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody CrearUsuarioRequest req) {
        var u = authService.crearPorAdmin(req.nombre(), req.correo(), req.password(), req.rol());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Cuenta " + u.getRol() + " creada para " + u.getCorreo() + ".");
        m.put("usuario", ApiMapper.usuario(u));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PatchMapping("/{id}/rol")
    public Map<String, Object> cambiarRol(@PathVariable Long id, @RequestBody CambiarRolRequest req) {
        var u = authService.cambiarRol(id, req.rol(), Seguridad.actual().orElseThrow().id());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "La cuenta " + u.getCorreo() + " ahora es " + u.getRol() + ".");
        m.put("usuario", ApiMapper.usuario(u));
        return m;
    }

    @PatchMapping("/{id}/activo")
    public Map<String, Object> activo(@PathVariable Long id, @RequestBody CambiarActivoRequest req) {
        var u = authService.cambiarActivo(id, req.activo(), Seguridad.actual().orElseThrow().id());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mensaje", "Cuenta " + u.getCorreo() + (req.activo() ? " activada." : " desactivada."));
        m.put("usuario", ApiMapper.usuario(u));
        return m;
    }
}
