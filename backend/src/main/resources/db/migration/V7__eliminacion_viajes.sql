-- Conservamos la referencia del álbum aunque el itinerario ya no se consulte.
ALTER TABLE viaje
    ADD COLUMN eliminado_en timestamptz,
    ADD COLUMN eliminando_recuerdos boolean NOT NULL DEFAULT false;
