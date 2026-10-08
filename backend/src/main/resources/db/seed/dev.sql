-- Datos locales de demostración. Ejecutar después de V1; no se aplican
-- automáticamente al iniciar la aplicación.
-- Cuenta de ejemplo: demo@tripmemories.local / Demo1234!
-- Las rutas demo:// son marcadores: no contienen archivos físicos.

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuario (id, nombre, correo, password_hash) VALUES
('11111111-1111-4111-8111-111111111111', 'Usuario Demo',
 'demo@tripmemories.local', crypt('Demo1234!', gen_salt('bf', 10)))
ON CONFLICT DO NOTHING;

INSERT INTO viaje (id, usuario_id, titulo, destino, tipo, fecha_inicio, fecha_fin) VALUES
('22222222-2222-4222-8222-222222222221', '11111111-1111-4111-8111-111111111111',
 'Madrid en dos días', 'Madrid', 'planificado', DATE '2026-09-14', DATE '2026-09-15'),
('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111',
 'Escapada a Valencia', 'Valencia', 'externo', DATE '2026-09-05', DATE '2026-09-06')
ON CONFLICT DO NOTHING;

INSERT INTO preferencias (viaje_id, intereses, exclusiones, nivel_gasto, distancia_max_km) VALUES
('22222222-2222-4222-8222-222222222221', ARRAY['arte', 'gastronomia'],
 ARRAY['vida nocturna'], 'medio', 8.00)
ON CONFLICT DO NOTHING;

INSERT INTO itinerario (id, viaje_id, version, es_actual) VALUES
('33333333-3333-4333-8333-333333333331', '22222222-2222-4222-8222-222222222221', 1, false),
('33333333-3333-4333-8333-333333333332', '22222222-2222-4222-8222-222222222221', 2, true)
ON CONFLICT DO NOTHING;

INSERT INTO actividad
    (id, itinerario_id, dia, orden, hora_aproximada, duracion_min, tipo, lugar_id, lugar_nombre)
VALUES
('44444444-4444-4444-8444-444444444441', '33333333-3333-4333-8333-333333333331',
 DATE '2026-09-14', 1, TIME '10:00', 120, 'museo', 'geoapify:prado-demo', 'Museo del Prado'),
('44444444-4444-4444-8444-444444444442', '33333333-3333-4333-8333-333333333332',
 DATE '2026-09-14', 1, TIME '10:30', 120, 'museo', 'geoapify:prado-demo', 'Museo del Prado'),
('44444444-4444-4444-8444-444444444443', '33333333-3333-4333-8333-333333333332',
 DATE '2026-09-14', 2, TIME '13:00', 60, 'paseo', 'geoapify:retiro-demo', 'Parque del Retiro')
ON CONFLICT DO NOTHING;

INSERT INTO album (id, viaje_id, titulo, es_privado) VALUES
('55555555-5555-4555-8555-555555555551', '22222222-2222-4222-8222-222222222221', 'Recuerdos de Madrid', true),
('55555555-5555-4555-8555-555555555552', '22222222-2222-4222-8222-222222222222', 'Recuerdos de Valencia', true)
ON CONFLICT DO NOTHING;

INSERT INTO archivo_multimedia
    (id, album_id, tipo, ruta_privada, descripcion, actividad_id, lugar_id_libre, lugar_nombre_libre)
VALUES
('66666666-6666-4666-8666-666666666661', '55555555-5555-4555-8555-555555555551',
 'foto', 'demo://madrid/prado.jpg', 'Visita al museo',
 '44444444-4444-4444-8444-444444444441', NULL, NULL),
('66666666-6666-4666-8666-666666666662', '55555555-5555-4555-8555-555555555552',
 'video', 'demo://valencia/mercado.mp4', 'Visita improvisada',
 NULL, 'geoapify:mercado-central-demo', 'Mercado Central')
ON CONFLICT DO NOTHING;

COMMIT;
