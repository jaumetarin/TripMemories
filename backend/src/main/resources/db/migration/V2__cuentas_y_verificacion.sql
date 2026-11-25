-- Las cuentas previas tampoco se consideran verificadas sin demostrar acceso al correo.
ALTER TABLE usuario ADD COLUMN correo_verificado boolean NOT NULL DEFAULT false;

CREATE TABLE verificacion_correo (
    usuario_id uuid PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expira_en timestamp with time zone NOT NULL,
    ultimo_envio_en timestamp with time zone NOT NULL
);
