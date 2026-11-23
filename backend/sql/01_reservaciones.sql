-- =====================================================================
--  Reservaciones (TerraNova) · ejecutar UNA VEZ en MySQL Workbench sobre terranova_db
--  La aplicación tiene ddl-auto=none, así que esta tabla se crea a mano.
--  NO modifica hotel, tipo_habitacion, habitacion ni estancia.
--  (La tabla SPRING_AI_CHAT_MEMORY de la memoria del chat la crea sola Spring AI al arrancar.)
-- =====================================================================
USE terranova_db;

CREATE TABLE IF NOT EXISTS reservacion (
    id_reservacion     BIGINT        NOT NULL AUTO_INCREMENT,
    id_habitacion      BIGINT        NOT NULL,
    nombre_huesped     VARCHAR(120)  NOT NULL,
    telefono           VARCHAR(20)   NOT NULL,
    correo             VARCHAR(120)  NULL,
    ciudad_procedencia VARCHAR(80)   NULL,
    fecha_entrada      DATE          NOT NULL,
    fecha_salida       DATE          NOT NULL,
    adultos            INT           NOT NULL DEFAULT 1,
    ninos              INT           NOT NULL DEFAULT 0,
    noches             INT           NOT NULL,
    precio_noche       DECIMAL(10,2) NOT NULL,   -- tarifa del tipo al momento de reservar
    costo_total        DECIMAL(12,2) NOT NULL,   -- noches x precio_noche
    estado             VARCHAR(20)   NOT NULL DEFAULT 'Confirmada',  -- Confirmada | Pendiente | Cancelada
    origen             VARCHAR(10)   NOT NULL DEFAULT 'WEB',         -- WEB | IA
    fecha_registro     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_reservacion),
    CONSTRAINT fk_reservacion_habitacion FOREIGN KEY (id_habitacion) REFERENCES habitacion (id_habitacion),
    CONSTRAINT ck_reservacion_fechas CHECK (fecha_salida > fecha_entrada),
    INDEX idx_reservacion_habitacion_fechas (id_habitacion, fecha_entrada, fecha_salida)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Datos de ejemplo. Usan las habitaciones que ya tienes (las primeras de hoteles activos que no estén
--  en mantenimiento) y fechas relativas a HOY, así que no dependen de nombres ni números de habitación.
--  Ejecuta esta parte solo si la tabla está vacía.
-- ---------------------------------------------------------------------
INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Familia López', '7121002233', 'lopez@ejemplo.com', 'Toluca', CURDATE() + INTERVAL 3 DAY,
       CURDATE() + INTERVAL 5 DAY, 2, 0, 2, t.precio_base, t.precio_base * 2, 'Confirmada', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 0) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Carlos Mendoza', '7129881122', 'carlos.m@ejemplo.com', 'CDMX', CURDATE() + INTERVAL 2 DAY,
       CURDATE() + INTERVAL 5 DAY, 1, 0, 3, t.precio_base, t.precio_base * 3, 'Confirmada', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 1) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Ana Sofía Martínez', '7225551234', 'ana.martinez@ejemplo.com', 'Querétaro', CURDATE() + INTERVAL 7 DAY,
       CURDATE() + INTERVAL 10 DAY, 2, 0, 3, t.precio_base, t.precio_base * 3, 'Confirmada', 'IA'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 2) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Jorge Ramírez Soto', '5512345678', NULL, 'Puebla', CURDATE() + INTERVAL 10 DAY,
       CURDATE() + INTERVAL 12 DAY, 1, 0, 2, t.precio_base, t.precio_base * 2, 'Pendiente', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 3) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Lucía Fernández Díaz', '7221110000', 'lucia.f@ejemplo.com', 'Guadalajara', CURDATE() + INTERVAL 1 DAY,
       CURDATE() + INTERVAL 4 DAY, 2, 0, 3, t.precio_base, t.precio_base * 3, 'Confirmada', 'IA'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 4) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Roberto Salinas Cruz', '7771234567', NULL, 'Cuernavaca', CURDATE() + INTERVAL 14 DAY,
       CURDATE() + INTERVAL 16 DAY, 1, 0, 2, t.precio_base, t.precio_base * 2, 'Cancelada', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 5) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Daniela Ortega Pineda', '5598765432', 'daniela.o@ejemplo.com', 'Monterrey', CURDATE() + INTERVAL 20 DAY,
       CURDATE() + INTERVAL 25 DAY, 2, 0, 5, t.precio_base, t.precio_base * 5, 'Confirmada', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 6) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

INSERT INTO reservacion (id_habitacion, nombre_huesped, telefono, correo, ciudad_procedencia, fecha_entrada,
                         fecha_salida, adultos, ninos, noches, precio_noche, costo_total, estado, origen)
SELECT ha.id_habitacion, 'Miguel Ángel Torres', '7224445566', NULL, 'Toluca', CURDATE() + INTERVAL 5 DAY,
       CURDATE() + INTERVAL 6 DAY, 1, 0, 1, t.precio_base, t.precio_base * 1, 'Confirmada', 'WEB'
FROM (SELECT x.id_habitacion, x.id_tipo FROM habitacion x
      INNER JOIN hotel hx ON hx.id_hotel = x.id_hotel
      WHERE hx.activo = TRUE AND x.estado <> 'Mantenimiento'
      ORDER BY x.id_habitacion LIMIT 1 OFFSET 7) ha
INNER JOIN tipo_habitacion t ON t.id_tipo = ha.id_tipo;

-- ---------------------------------------------------------------------
--  Comprobación
-- ---------------------------------------------------------------------
SELECT r.id_reservacion AS folio, h.nombre AS hotel, ha.numero AS habitacion, r.nombre_huesped,
       r.fecha_entrada, r.fecha_salida, r.noches, r.costo_total, r.estado, r.origen
FROM reservacion r
INNER JOIN habitacion ha ON r.id_habitacion = ha.id_habitacion
INNER JOIN hotel h ON ha.id_hotel = h.id_hotel
ORDER BY r.fecha_entrada;

-- Para borrar todo y volver a empezar:  DROP TABLE reservacion;
-- Para vaciar la memoria del chat:       TRUNCATE TABLE SPRING_AI_CHAT_MEMORY;
