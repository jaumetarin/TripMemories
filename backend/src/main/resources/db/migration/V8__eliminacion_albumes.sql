-- Permite reintentar un borrado sin servir fotos retiradas parcialmente del disco.
ALTER TABLE album ADD COLUMN eliminando boolean NOT NULL DEFAULT false;
