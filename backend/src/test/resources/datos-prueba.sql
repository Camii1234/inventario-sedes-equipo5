-- Mismos datos que la migración V2 de MySQL.
INSERT INTO almacen (id, nombre, ciudad, direccion) VALUES
    (1, 'Sede Medellín Centro', 'Medellín', 'Carrera 50 # 52-20'),
    (2, 'Sede Bogotá Norte', 'Bogotá', 'Calle 127 # 15-40'),
    (3, 'Sede Cali Sur', 'Cali', 'Carrera 100 # 16-05');
ALTER TABLE almacen ALTER COLUMN id RESTART WITH 4;

INSERT INTO producto (almacen_id, sku, nombre, stock, creado_en) VALUES
    (1, 'LEC-001', 'Leche entera 1 L', 40, TIMESTAMP '2026-11-27 08:00:00'),
    (1, 'ACE-001', 'Aceite vegetal 1 L', 35, TIMESTAMP '2026-11-27 08:00:00'),
    (1, 'AZU-001', 'Azúcar refinada 1 kg', 50, TIMESTAMP '2026-11-27 08:00:00'),
    (2, 'ACE-001', 'Aceite vegetal 1 L', 12, TIMESTAMP '2026-11-27 08:00:00'),
    (2, 'CAF-001', 'Café molido 500 g', 18, TIMESTAMP '2026-11-27 08:00:00'),
    (2, 'HAR-001', 'Harina de trigo 1 kg', 25, TIMESTAMP '2026-11-27 08:00:00');
