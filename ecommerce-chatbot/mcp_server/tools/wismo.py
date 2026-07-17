def get_order_status(order_id):
    """
    Connects to MuleSoft to fetch SAP S/4HANA logistics data.
    """
    print(f"Calling MuleSoft endpoint for Order ID: {order_id}")
    # Mocking MuleSoft API call
    return {
        "order_id": order_id,
        "status": "SHIPPED",
        "estimated_delivery": "2026-07-15",
        "carrier": "FedEx"
    }
