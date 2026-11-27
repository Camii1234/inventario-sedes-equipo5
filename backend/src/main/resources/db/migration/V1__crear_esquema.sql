-- Esquema del inventario por sede.
-- Cada fila de producto representa el inventario de un SKU en una sede.

CREATE TABLE almacen (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    nombre    VARCHAR(100) NOT NULL,
    ciudad    VARCHAR(80)  NOT NULL,
    direccion VARCHAR(150) NULL,
    CONSTRAINT pk_almacen PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE producto (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    almacen_id BIGINT       NOT NULL,
    sku        VARCHAR(40)  NOT NULL,
    nombre     VARCHAR(120) NOT NULL,
    stock      INT          NOT NULL,
    creado_en  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_producto PRIMARY KEY (id),
    CONSTRAINT fk_producto_almacen FOREIGN KEY (almacen_id) REFERENCES almacen (id),
    CONSTRAINT uk_producto_almacen_sku UNIQUE (almacen_id, sku),
    CONSTRAINT ck_producto_stock_no_negativo CHECK (stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
