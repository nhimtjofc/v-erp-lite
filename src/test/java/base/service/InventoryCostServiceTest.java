package base.service;

import base.entity.Order;
import base.entity.OrderItem;
import base.entity.Product;
import base.entity.ProductVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryCostServiceTest {

    private final InventoryCostService service = new InventoryCostService();

    @Test
    void variantCostOverridesBaseProductCost() {
        Product product = new Product();
        product.setCostPriceMinor(80_000);
        ProductVariant variant = new ProductVariant();
        variant.setCostPriceMinor(95_000L);

        assertEquals(95_000, service.resolveUnitCost(product, variant));
        variant.setCostPriceMinor(null);
        assertEquals(80_000, service.resolveUnitCost(product, variant));
    }

    @Test
    void orderCostUsesImmutableItemSnapshots() {
        OrderItem first = new OrderItem();
        first.setUnitCostMinor(40_000);
        first.setQuantity(2);
        OrderItem second = new OrderItem();
        second.setUnitCostMinor(15_000);
        second.setQuantity(3);
        Order order = new Order();
        order.setItems(List.of(first, second));

        assertEquals(125_000, service.orderCost(order));
    }
}
