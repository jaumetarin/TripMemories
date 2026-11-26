-- Un álbum privado por cada viaje, también para los guardados antes de esta UT.
INSERT INTO album(viaje_id, titulo)
SELECT id, left('Recuerdos de ' || COALESCE(destino, titulo), 160) FROM viaje
ON CONFLICT (viaje_id) DO NOTHING;

ALTER TABLE archivo_multimedia
    ADD COLUMN nombre_original varchar(200),
    ADD COLUMN mime_type varchar(30),
    ADD COLUMN bytes_original bigint NOT NULL DEFAULT 0 CHECK (bytes_original >= 0),
    ADD COLUMN bytes_miniatura bigint NOT NULL DEFAULT 0 CHECK (bytes_miniatura >= 0),
    ADD COLUMN ruta_miniatura text,
    ADD COLUMN ancho integer,
    ADD COLUMN alto integer,
    ADD COLUMN estado varchar(20) NOT NULL DEFAULT 'LEGADO'
        CHECK (estado IN ('LEGADO', 'PENDIENTE', 'DISPONIBLE', 'ERROR', 'ELIMINANDO'));

ALTER TABLE archivo_multimedia ADD CONSTRAINT ck_foto_disponible CHECK (
    estado <> 'DISPONIBLE' OR (
        tipo = 'foto' AND nombre_original IS NOT NULL
        AND mime_type IS NOT NULL AND mime_type IN ('image/jpeg', 'image/png')
        AND bytes_original > 0 AND bytes_miniatura > 0
        AND ruta_miniatura IS NOT NULL AND ancho IS NOT NULL AND alto IS NOT NULL AND ancho > 0 AND alto > 0
    )
);
