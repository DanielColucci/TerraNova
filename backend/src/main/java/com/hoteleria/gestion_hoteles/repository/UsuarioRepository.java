package com.hoteleria.gestion_hoteles.repository;

import com.hoteleria.gestion_hoteles.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    Optional<Usuario> findByTokenHash(String tokenHash);
    boolean existsByIdUsuarioAndActivoTrue(Long idUsuario);
    List<Usuario> findAllByOrderByFechaRegistroDesc();
    List<Usuario> findByRolOrderByFechaRegistroDesc(com.hoteleria.gestion_hoteles.entity.Rol rol);
    long countByRolAndActivoTrue(com.hoteleria.gestion_hoteles.entity.Rol rol);
}
