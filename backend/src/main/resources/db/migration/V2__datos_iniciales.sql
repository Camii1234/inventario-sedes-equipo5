-- Datos iniciales. Flyway aplica esta migración una sola vez (tabla flyway_schema_history),
-- por lo que reiniciar los contenedores no duplica la carga.
--   Sede 1 y Sede 2 tienen productos; ACE-001 existe en ambas con stock distinto (35 y 12).
--   Sede 3 queda vacía para probar el inventario sin productos.

INSERT INTO almacen (id, nombre, ciudad, direccion) VALUES
    (1, 'Sede Medellín Centro', 'Medellín', 'Carrera 50 # 52-20'),
    (2, 'Sede Bogotá Norte',    'Bogotá',   'Calle 127 # 15-40'),
    (3, 'Sede Cali Sur',        'Cali',     'Carrera 100 # 16-05');

INSERT INTO producto (almacen_id, sku, nombre, stock, creado_en) VALUES
    (1, 'ACE-001', 'Aceite vegetal 1 L',      35, '2026-11-27 08:00:00.000000'),
    (1, 'AZU-001', 'Azúcar refinada 1 kg',    50, '2026-11-27 08:00:00.000000'),
    (1, 'LEC-001', 'Leche entera 1 L',        40, '2026-11-27 08:00:00.000000'),
    (2, 'ACE-001', 'Aceite vegetal 1 L',      12, '2026-11-27 08:00:00.000000'),
    (2, 'CAF-001', 'Café molido 500 g',       18, '2026-11-27 08:00:00.000000'),
    (2, 'HAR-001', 'Harina de trigo 1 kg',    25, '2026-11-27 08:00:00.000000');
