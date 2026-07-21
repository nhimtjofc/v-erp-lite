package base.service;

import base.entity.Order;
import base.entity.OrderItem;
import base.entity.Product;
import base.entity.ProductVariant;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InventoryCostService {

    public long resolveUnitCost(Product product, ProductVariant variant) {
        if (variant != null && variant.getCostPriceMinor() != null) {
            return variant.getCostPriceMinor();
        }
        return product == null ? 0 : product.getCostPriceMinor();
    }

    public long orderCost(Order order) {
        if (order == null || order.getItems() == null) {
            return 0;
        }
        return order.getItems().stream()
                .mapToLong(this::itemCost)
                .sum();
    }

    private long itemCost(OrderItem item) {
        return Math.multiplyExact(item.getUnitCostMinor(), item.getQuantity());
    }
}
