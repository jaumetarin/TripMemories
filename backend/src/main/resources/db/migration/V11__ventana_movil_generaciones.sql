CREATE TABLE generacion_reciente (
    usuario_id uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    instante timestamptz NOT NULL
);
CREATE INDEX generacion_reciente_cuenta_instante ON generacion_reciente(usuario_id, instante);
-- El contador anterior no conserva las horas individuales. Retener las reservas
-- activas durante una hora desde la migracion para no liberar consumo prematuramente.
INSERT INTO generacion_reciente(usuario_id, instante)
SELECT usuario_id, CURRENT_TIMESTAMP
FROM consumo_generacion
CROSS JOIN LATERAL generate_series(1, usadas_hora)
WHERE inicio_hora > CURRENT_TIMESTAMP - INTERVAL '1 hour';
ALTER TABLE consumo_generacion DROP COLUMN inicio_hora, DROP COLUMN usadas_hora;
