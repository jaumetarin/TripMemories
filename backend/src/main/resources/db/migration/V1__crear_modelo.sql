-- Esquema del modelo de dominio acordado en la UT2.
-- Los ID externos de lugar (por ejemplo geoapify:<id>) no son claves foráneas.

CREATE TABLE usuario (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre varchar(120) NOT NULL CHECK (btrim(nombre) <> ''),
    correo varchar(255) NOT NULL CHECK (btrim(correo) <> ''),
    password_hash varchar(255) NOT NULL CHECK (btrim(password_hash) <> '')
);

CREATE UNIQUE INDEX uq_usuario_correo_ci ON usuario (lower(correo));

CREATE TABLE viaje (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    titulo varchar(160) NOT NULL CHECK (btrim(titulo) <> ''),
    destino varchar(160) NOT NULL CHECK (btrim(destino) <> ''),
    tipo varchar(12) NOT NULL CHECK (tipo IN ('planificado', 'externo')),
    fecha_inicio date,
    fecha_fin date,
    CONSTRAINT ck_viaje_fechas CHECK (
        (fecha_inicio IS NULL AND fecha_fin IS NULL) OR
        (fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL AND fecha_fin >= fecha_inicio)
    ),
    CONSTRAINT ck_viaje_plan_max_10_dias CHECK (
        tipo <> 'planificado' OR fecha_inicio IS NULL OR fecha_fin - fecha_inicio BETWEEN 0 AND 9
    )
);

CREATE INDEX ix_viaje_usuario ON viaje (usuario_id);

CREATE TABLE preferencias (
    viaje_id uuid PRIMARY KEY REFERENCES viaje(id),
    intereses text[] NOT NULL DEFAULT '{}',
    exclusiones text[] NOT NULL DEFAULT '{}',
    nivel_gasto varchar(10) NOT NULL CHECK (nivel_gasto IN ('bajo', 'medio', 'alto')),
    distancia_max_km numeric(6,2) NOT NULL CHECK (distancia_max_km > 0)
);

CREATE TABLE itinerario (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    viaje_id uuid NOT NULL REFERENCES viaje(id),
    version integer NOT NULL CHECK (version > 0),
    es_actual boolean NOT NULL DEFAULT false,
    UNIQUE (viaje_id, version)
);

CREATE UNIQUE INDEX uq_itinerario_actual_por_viaje
    ON itinerario (viaje_id) WHERE es_actual;

CREATE TABLE actividad (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    itinerario_id uuid NOT NULL REFERENCES itinerario(id),
    dia date NOT NULL,
    orden integer NOT NULL CHECK (orden > 0),
    hora_aproximada time,
    duracion_min integer CHECK (duracion_min > 0),
    tipo varchar(60) NOT NULL CHECK (btrim(tipo) <> ''),
    lugar_id text,
    lugar_nombre varchar(200),
    UNIQUE (itinerario_id, dia, orden)
);

CREATE TABLE album (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    viaje_id uuid NOT NULL UNIQUE REFERENCES viaje(id),
    titulo varchar(160) NOT NULL CHECK (btrim(titulo) <> ''),
    es_privado boolean NOT NULL DEFAULT true CHECK (es_privado)
);

CREATE TABLE archivo_multimedia (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id uuid NOT NULL REFERENCES album(id),
    tipo varchar(5) NOT NULL CHECK (tipo IN ('foto', 'video')),
    ruta_privada text NOT NULL CHECK (btrim(ruta_privada) <> ''),
    subido_en timestamptz NOT NULL DEFAULT now(),
    descripcion text,
    actividad_id uuid REFERENCES actividad(id),
    lugar_id_libre text,
    lugar_nombre_libre varchar(200),
    CONSTRAINT ck_lugar_archivo CHECK (
        actividad_id IS NULL OR (lugar_id_libre IS NULL AND lugar_nombre_libre IS NULL)
    )
);

CREATE INDEX ix_archivo_album ON archivo_multimedia (album_id);
CREATE INDEX ix_archivo_actividad ON archivo_multimedia (actividad_id)
    WHERE actividad_id IS NOT NULL;

-- Al vincular un archivo con una actividad, ambos deben pertenecer al mismo
-- viaje. Se comprueba también en la base de datos para impedir enlaces cruzados.
CREATE FUNCTION comprobar_archivo_mismo_viaje() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    viaje_album uuid;
    viaje_actividad uuid;
BEGIN
    IF NEW.actividad_id IS NOT NULL THEN
        SELECT viaje_id INTO viaje_album FROM album WHERE id = NEW.album_id;
        SELECT i.viaje_id INTO viaje_actividad
          FROM actividad a JOIN itinerario i ON i.id = a.itinerario_id
         WHERE a.id = NEW.actividad_id;
        IF viaje_album IS DISTINCT FROM viaje_actividad THEN
            RAISE EXCEPTION 'El archivo y la actividad deben pertenecer al mismo viaje'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_archivo_mismo_viaje
BEFORE INSERT OR UPDATE OF album_id, actividad_id ON archivo_multimedia
FOR EACH ROW EXECUTE FUNCTION comprobar_archivo_mismo_viaje();
