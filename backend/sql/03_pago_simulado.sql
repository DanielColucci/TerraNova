-- =====================================================================
--  Pago simulado + estado Pendiente_Pago · ejecutar UNA VEZ sobre terranova_db
-- =====================================================================
USE terranova_db;

-- El estado de la reservación es VARCHAR(20): ahora admite 'Pendiente_Pago' (14 caracteres).
-- (Si en tu base la columna fuera un ENUM de MySQL, este MODIFY la convierte a VARCHAR.)
ALTER TABLE reservacion MODIFY COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'Confirmada';   -- Confirmada | Pendiente | Pendiente_Pago | Cancelada
ALTER TABLE reservacion ADD INDEX idx_reservacion_estado_registro (estado, fecha_registro);

CREATE TABLE IF NOT EXISTS pago (
    id_pago        BIGINT        NOT NULL AUTO_INCREMENT,
    id_reservacion BIGINT        NOT NULL,
    monto          DECIMAL(12,2) NOT NULL,
    metodo         VARCHAR(20)   NOT NULL DEFAULT 'TARJETA',
    marca          VARCHAR(20)   NULL,
    ultimos4       VARCHAR(4)    NOT NULL,          -- NUNCA se guarda el número completo ni el CVV
    titular        VARCHAR(120)  NOT NULL,
    estado         VARCHAR(12)   NOT NULL,          -- Aprobado | Rechazado
    referencia     VARCHAR(30)   NOT NULL,
    fecha          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_pago),
    UNIQUE KEY uk_pago_referencia (referencia),
    KEY idx_pago_reservacion (id_reservacion),
    CONSTRAINT fk_pago_reservacion FOREIGN KEY (id_reservacion) REFERENCES reservacion (id_reservacion)
) ENGINE=InnoDB;
