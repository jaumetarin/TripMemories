-- Fechas de almacenamiento y control de actualizaciones.
ALTER TABLE viaje
    ADD COLUMN creado_en timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN actualizado_en timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN revision bigint NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_viaje_revision_positiva
        CHECK (revision > 0);

-- Configuración y datos generales, sin duplicar los bloques.
ALTER TABLE itinerario
    ADD COLUMN configuracion jsonb,
    ADD COLUMN datos_generacion jsonb,
    ADD COLUMN formato integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_itinerario_configuracion_objeto
        CHECK (
            configuracion IS NULL
            OR jsonb_typeof(configuracion) = 'object'
        ),
    ADD CONSTRAINT ck_itinerario_generacion_objeto
        CHECK (
            datos_generacion IS NULL
            OR jsonb_typeof(datos_generacion) = 'object'
        ),
    ADD CONSTRAINT ck_itinerario_formato_positivo
        CHECK (formato > 0);

-- Inicio: las columnas existentes dia + hora_aproximada.
-- Fin: fecha y hora completas, para conservar el límite exacto.
ALTER TABLE actividad
    ADD COLUMN fin timestamp without time zone,
    ADD COLUMN detalle jsonb,
    ADD CONSTRAINT ck_actividad_detalle_objeto
        CHECK (
            detalle IS NULL
            OR jsonb_typeof(detalle) = 'object'
        ),
    ADD CONSTRAINT ck_actividad_intervalo
        CHECK (
            fin IS NULL
            OR hora_aproximada IS NULL
            OR fin >= dia + hora_aproximada
        );

-- Listado de viajes propios por última modificación.
CREATE INDEX ix_viaje_usuario_actualizacion
    ON viaje (usuario_id, actualizado_en DESC, id);
