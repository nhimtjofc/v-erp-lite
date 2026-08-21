package base.rest;

import base.entity.Order;
import base.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void paidEscrowCancellationTargetsEveryActiveItem() {
        OrderItem requestedItem = new OrderItem();
        OrderItem otherActiveItem = new OrderItem();
        OrderItem alreadyCancelledItem = new OrderItem();
        alreadyCancelledItem.setCancelledAt(Instant.now());

        Order order = new Order();
        order.setStatus("PAID_ESCROW");
        order.setItems(List.of(requestedItem, otherActiveItem, alreadyCancelledItem));

        List<OrderItem> targets = OrderResource.cancellationTargets(order, requestedItem);

        assertEquals(2, targets.size());
        assertTrue(targets.contains(requestedItem));
        assertTrue(targets.contains(otherActiveItem));
        assertFalse(targets.contains(alreadyCancelledItem));
    }
}
