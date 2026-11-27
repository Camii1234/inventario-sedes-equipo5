package co.edu.udea.arqsoft.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Inventario de un SKU en una sede. El mismo SKU puede existir en varias sedes,
 * pero solo una vez por sede (restricción única almacen_id + sku).
 */
@Entity
@Table(name = "producto",
        uniqueConstraints = @UniqueConstraint(name = "uk_producto_almacen_sku", columnNames = {"almacen_id", "sku"}))
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "almacen_id", nullable = false)
    private Almacen almacen;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Producto() {
    }

    public Producto(Almacen almacen, String sku, String nombre, Integer stock) {
        this.almacen = almacen;
        this.sku = sku;
        this.nombre = nombre;
        this.stock = stock;
    }

    @PrePersist
    void alPersistir() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Almacen getAlmacen() {
        return almacen;
    }

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getStock() {
        return stock;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
