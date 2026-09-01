package com.auracraft.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for Inventory – only productVariant (warehouse removed).
 */
class InventoryId implements Serializable {
    private Integer productVariant;

    public InventoryId() {}

    public InventoryId(Integer productVariant) {
        this.productVariant = productVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryId that = (InventoryId) o;
        return Objects.equals(this.productVariant, that.productVariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.productVariant);
    }
}
