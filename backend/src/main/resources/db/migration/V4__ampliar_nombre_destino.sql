-- Coincide con el límite admitido por ConfiguracionItinerarioService.
ALTER TABLE viaje
    ALTER COLUMN destino TYPE varchar(200);
