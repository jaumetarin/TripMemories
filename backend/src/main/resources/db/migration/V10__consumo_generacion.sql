CREATE TABLE consumo_generacion (
    usuario_id uuid PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    mes date NOT NULL,
    usadas_mes integer NOT NULL DEFAULT 0 CHECK (usadas_mes >= 0),
    inicio_hora timestamptz NOT NULL,
    usadas_hora integer NOT NULL DEFAULT 0 CHECK (usadas_hora >= 0)
);
CREATE TABLE limite_proveedor (
    usuario_id uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    clase varchar(20) NOT NULL,
    inicio timestamptz NOT NULL,
    usadas integer NOT NULL CHECK (usadas >= 0),
    PRIMARY KEY (usuario_id, clase)
);
