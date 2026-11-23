package com.hoteleria.gestion_hoteles.auth;

import com.hoteleria.gestion_hoteles.entity.Rol;
import com.hoteleria.gestion_hoteles.entity.Usuario;
import com.hoteleria.gestion_hoteles.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Si la tabla usuario está vacía crea dos cuentas de prueba ya verificadas (contraseñas con BCrypt). */
@Component
public class UsuarioDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioDataInitializer.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public UsuarioDataInitializer(UsuarioRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        try {
            if (repo.count() > 0) {
                return;
            }
            crear("Administrador TerraNova", "admin@terranova.com", "Admin123!", Rol.ADMINISTRADOR);
            crear("Cliente de prueba", "cliente@gmail.com", "Cliente123!", Rol.USUARIO);
            log.info("[AUTH] Cuentas de prueba creadas: admin@terranova.com / Admin123!  y  cliente@gmail.com / Cliente123!");
        } catch (DataAccessException e) {
            log.warn("[AUTH] No se pudo consultar la tabla usuario. ¿Ejecutaste sql/usuarios.sql en MySQL? ({})", e.getMessage());
        }
    }

    private void crear(String nombre, String correo, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setPassword(encoder.encode(password));
        u.setRol(rol);
        u.setVerified(true);
        repo.save(u);
    }
}
