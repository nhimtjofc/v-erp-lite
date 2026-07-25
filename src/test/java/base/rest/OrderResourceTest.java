package base.rest;

import base.entity.Order;
import base.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderResourceTest {

    @Test
    void orderIsCancelledOnlyAfterEveryItemIsCancelled() {
        OrderItem cancelledItem = new OrderItem();
        cancelledItem.setCancelledAt(Instant.now());
        OrderItem activeItem = new OrderItem();

        Order order = new Order();
        order.setItems(List.of(cancelledItem, activeItem));

        assertFalse(OrderResource.areAllItemsCancelled(order));

        activeItem.setCancelledAt(Instant.now());

        assertTrue(OrderResource.areAllItemsCancelled(order));
    }

    @Test
    void emptyOrderIsNotTreatedAsBuyerCancelled() {
        Order order = new Order();
        order.setItems(List.of());

        assertFalse(OrderResource.areAllItemsCancelled(order));
    }
}
