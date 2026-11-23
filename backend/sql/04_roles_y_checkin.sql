-- =====================================================================
--  03_roles_y_checkin.sql · TerraNova · MySQL 8.0.16+ · MySQL Workbench
--  Parte A: 4 roles (ADMINISTRADOR, GERENTE, RECEPCIONISTA, USUARIO)
--  Parte B: check-in real sobre una reservación existente
--  Re-ejecutable (idempotente). La app usa ddl-auto=none: estos cambios son manuales.
--  Recomendado: respaldo previo ->  mysqldump terranova_db > respaldo_antes_03.sql
--
--  CORRECCIONES respecto a la versión anterior (causas de los fallos en Workbench):
--   1. Workbench trae «Safe Updates» activo: los UPDATE sin llave en el WHERE fallaban (error 1175).
--      Ahora se desactiva solo durante el script y se restaura al final.
--   2. Los procedimientos con DELIMITER $$ comparaban parámetros contra information_schema
--      (error 1267 «Illegal mix of collations» en MySQL 8) y Workbench corta el script con DELIMITER.
--      Ahora se usan variables de usuario + PREPARE, sin procedimientos ni DELIMITER.
--   3. Se asegura la columna usuario.activo (la usa el índice de roles) aunque no se haya corrido el 01.
--   4. Se verifica que el motor sea InnoDB antes de crear llaves foráneas.
-- =====================================================================
USE terranova_db;

SET @tn_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- =====================================================================
-- PARTE A · ROLES
-- =====================================================================
-- A0. Columna activo (por si no se ejecutó 01_usuario_verificacion_por_enlace.sql).
SET @n = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'activo');
SET @s = IF(@n = 0, 'ALTER TABLE usuario ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- A1. Normaliza valores existentes y manda a USUARIO cualquier rol desconocido.
UPDATE usuario SET rol = UPPER(TRIM(rol)) WHERE id_usuario > 0;
UPDATE usuario SET rol = 'USUARIO'
 WHERE id_usuario > 0 AND rol NOT IN ('ADMINISTRADOR', 'GERENTE', 'RECEPCIONISTA', 'USUARIO');

-- A2. VARCHAR(20) con default seguro ('RECEPCIONISTA' mide 13).
ALTER TABLE usuario MODIFY COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'USUARIO';

-- A3. Solo los 4 roles son válidos (MySQL 8.0.16+ aplica CHECK).
SET @n = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND CONSTRAINT_NAME = 'chk_usuario_rol');
SET @s = IF(@n = 0, 'ALTER TABLE usuario ADD CONSTRAINT chk_usuario_rol CHECK (rol IN (''ADMINISTRADOR'',''GERENTE'',''RECEPCIONISTA'',''USUARIO''))', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- A4. Consultas por rol.
SET @n = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND INDEX_NAME = 'idx_usuario_rol');
SET @s = IF(@n = 0, 'ALTER TABLE usuario ADD INDEX idx_usuario_rol (rol, activo)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- A5. Cuentas de personal: se crean desde la pantalla «Usuarios» del administrador (requieren hash BCrypt).
--     Para promover una cuenta existente:
--       UPDATE usuario SET rol = 'RECEPCIONISTA' WHERE correo = 'recepcion@terranova.com';

-- =====================================================================
-- PARTE B · CHECK-IN SOBRE RESERVACIÓN
-- =====================================================================
-- Verificación de motor: las llaves foráneas exigen InnoDB en las tres tablas.
SELECT TABLE_NAME AS tabla, ENGINE AS motor FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('estancia', 'reservacion', 'usuario');

-- B1. estancia: reservación de origen + quién procesó el check-in (ambas admiten NULL).
SET @n = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND COLUMN_NAME = 'id_reservacion');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD COLUMN id_reservacion BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND COLUMN_NAME = 'id_usuario_checkin');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD COLUMN id_usuario_checkin BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND INDEX_NAME = 'uk_estancia_reservacion');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD UNIQUE INDEX uk_estancia_reservacion (id_reservacion)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND INDEX_NAME = 'idx_estancia_usuario_checkin');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD INDEX idx_estancia_usuario_checkin (id_usuario_checkin)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND CONSTRAINT_NAME = 'fk_estancia_reservacion');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD CONSTRAINT fk_estancia_reservacion FOREIGN KEY (id_reservacion) REFERENCES reservacion (id_reservacion)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'estancia' AND CONSTRAINT_NAME = 'fk_estancia_usuario_checkin');
SET @s = IF(@n = 0, 'ALTER TABLE estancia ADD CONSTRAINT fk_estancia_usuario_checkin FOREIGN KEY (id_usuario_checkin) REFERENCES usuario (id_usuario)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- B2. reservacion: marca de llegada real.
SET @n = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservacion' AND COLUMN_NAME = 'fecha_checkin');
SET @s = IF(@n = 0, 'ALTER TABLE reservacion ADD COLUMN fecha_checkin DATETIME(6) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @n = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservacion' AND INDEX_NAME = 'idx_reservacion_llegadas');
SET @s = IF(@n = 0, 'ALTER TABLE reservacion ADD INDEX idx_reservacion_llegadas (fecha_entrada, estado)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;


-- B3. Sincroniza la marca si ya hubiera estancias enlazadas.
UPDATE reservacion r
  JOIN estancia e ON e.id_reservacion = r.id_reservacion
   SET r.fecha_checkin = e.fecha_entrada
 WHERE r.id_reservacion > 0 AND r.fecha_checkin IS NULL;

-- ---------- restaura Safe Updates ----------
SET SQL_SAFE_UPDATES = @tn_safe_updates;

-- ---------- verificación rápida ----------
SELECT rol, COUNT(*) AS cuentas FROM usuario GROUP BY rol;
SHOW COLUMNS FROM estancia     LIKE 'id\_%';
SHOW COLUMNS FROM reservacion  LIKE 'fecha\_checkin';
