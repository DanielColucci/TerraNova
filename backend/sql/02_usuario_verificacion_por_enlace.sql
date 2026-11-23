-- =====================================================================
--  Verificación por ENLACE + activar/desactivar cuentas · ejecutar UNA VEZ sobre terranova_db
--  (la aplicación usa ddl-auto=none)
-- =====================================================================
USE terranova_db;

ALTER TABLE usuario ADD COLUMN token_hash   VARCHAR(64) NULL;             -- SHA-256 (hex) del token; el token en claro nunca se guarda
ALTER TABLE usuario ADD COLUMN token_expira DATETIME(6) NULL;             -- vigencia: 24 h
ALTER TABLE usuario ADD COLUMN activo       TINYINT(1)  NOT NULL DEFAULT 1;
ALTER TABLE usuario ADD INDEX idx_usuario_token_hash (token_hash);

-- Columnas del esquema anterior (código de 6 dígitos): ya no se usan.
ALTER TABLE usuario DROP COLUMN codigo_verificacion;
ALTER TABLE usuario DROP COLUMN codigo_expira;
