-- =====================================================================
--  Autenticación y roles (TerraNova) · ejecutar UNA VEZ en MySQL Workbench sobre terranova_db
--  La aplicación tiene ddl-auto=none, así que estos cambios se hacen a mano.
--  Las cuentas de prueba (admin@terranova.com / cliente@gmail.com) las crea la aplicación al arrancar.
-- =====================================================================
USE terranova_db;

CREATE TABLE IF NOT EXISTS usuario (
    id_usuario          BIGINT       NOT NULL AUTO_INCREMENT,
    nombre              VARCHAR(120) NOT NULL,
    correo              VARCHAR(120) NOT NULL,
    password            VARCHAR(100) NOT NULL,            -- hash BCrypt (60 caracteres), nunca texto plano
    is_verified         TINYINT(1)   NOT NULL DEFAULT 0,
    rol                 VARCHAR(20)  NOT NULL,            -- ADMINISTRADOR | USUARIO
    codigo_verificacion VARCHAR(6)   NULL,
    codigo_expira       DATETIME(6)  NULL,
    fecha_registro      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_usuario),
    UNIQUE KEY uk_usuario_correo (correo)
) ENGINE=InnoDB;

-- Cada reservación puede pertenecer a una cuenta (las hechas por un USUARIO). Las demás quedan en NULL.
-- (Ejecuta esta línea una sola vez; si la columna ya existe, MySQL avisará y puedes ignorarlo.)
ALTER TABLE reservacion ADD COLUMN id_usuario BIGINT NULL;
ALTER TABLE reservacion ADD INDEX idx_reservacion_usuario (id_usuario);
