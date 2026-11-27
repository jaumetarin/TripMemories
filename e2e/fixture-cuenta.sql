\set ON_ERROR_STOP on

-- Cuenta pública de pruebas. Ejecutar después de las migraciones en la BD aislada.
DO $$
BEGIN
    IF current_database() <> 'rute_avd' THEN
        RAISE EXCEPTION 'La cuenta de pruebas solo se prepara en rute_avd';
    END IF;
END $$;

INSERT INTO usuario (nombre, correo, password_hash, correo_verificado)
VALUES ('Usuario AVD', 'avd-e2e@rute.local', '{argon2id}$argon2id$v=19$m=19456,t=2,p=1$mpeL+EO7Fdi8NgZZF3NkAg$vowFjt57VwOF5AjIHGmS742duFEkCxWsaZFB6S6UYT4', true)
ON CONFLICT (lower(correo)) DO UPDATE
SET nombre = EXCLUDED.nombre,
    password_hash = EXCLUDED.password_hash,
    correo_verificado = EXCLUDED.correo_verificado;
