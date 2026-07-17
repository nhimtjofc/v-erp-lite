<?php

defined('ABSPATH') or exit;

class V_ERP_Order_Handler {

    const META_ERP_ORDER_ID = '_v_erp_order_id';
    const META_SYNC_STATUS = '_v_erp_sync_status';
    const META_SYNC_ERROR = '_v_erp_sync_error';
    const META_SYNCED_AT = '_v_erp_synced_at';
    const META_LAST_ATTEMPT = '_v_erp_last_attempt';
    const META_PAYLOAD = '_v_erp_last_payload';

    const STATUS_SYNCED = 'synced';
    const STATUS_FAILED = 'failed';
    const STATUS_PENDING = 'pending';

    public static function init() {
        add_action('woocommerce_new_order', [__CLASS__, 'on_new_order'], 20, 1);
    }

    public static function on_new_order($order_id) {
        $order = wc_get_order($order_id);
        if (!$order) {
            return;
        }

        $payment_method = $order->get_payment_method();
        if (!in_array($payment_method, ['cod', 'mock_bank'], true)) {
            return;
        }

        $secret = V_ERP_HMAC::get_secret();
        if (empty($secret)) {
            $order->add_order_note(__('[V-ERP] Bỏ qua đồng bộ: chưa cấu hình webhook secret.', 'v-erp-woo-checkout'));
            return;
        }

        self::submit_order($order);
    }

    public static function submit_order($order) {
        $order_id = $order->get_id();
        $idempotency_key = 'woo-order-create-' . $order_id;

        $payload = self::build_payload($order);
        $payload_json = json_encode(['order' => $payload], JSON_UNESCAPED_UNICODE);

        $order->update_meta_data(self::META_SYNC_STATUS, self::STATUS_PENDING);
        $order->update_meta_data(self::META_LAST_ATTEMPT, current_time('mysql'));
        $order->update_meta_data(self::META_PAYLOAD, $payload_json);
        $order->save();

        $tenant_id = V_ERP_Settings::get_tenant_id();
        $channel_id = V_ERP_Settings::get_sales_channel_id();
        $api_base = V_ERP_Settings::get_api_base();

        $endpoint = $api_base . '/integrations/woocommerce/v1/orders'
            . '?tenantId=' . urlencode($tenant_id)
            . '&salesChannelId=' . urlencode($channel_id);

        $secret = V_ERP_HMAC::get_secret();
        $signature = V_ERP_HMAC::sign($payload_json, $secret);

        $response = wp_remote_post($endpoint, [
            'headers' => [
                'Content-Type' => 'application/json',
                'X-WC-Webhook-Signature' => $signature,
                'X-WC-Webhook-Delivery-ID' => 'wp-' . uniqid('', true),
                'X-WC-Webhook-Topic' => 'order.created',
            ],
            'body' => $payload_json,
            'timeout' => 30,
            'data_format' => 'body',
        ]);

        if (is_wp_error($response)) {
            $error_message = $response->get_error_message();
            $order->update_meta_data(self::META_SYNC_STATUS, self::STATUS_FAILED);
            $order->update_meta_data(self::META_SYNC_ERROR, $error_message);
            $order->save();
            $order->add_order_note(sprintf(
                __('[V-ERP] Đồng bộ thất bại: %s', 'v-erp-woo-checkout'),
                $error_message
            ));
            return;
        }

        $status_code = wp_remote_retrieve_response_code($response);
        $body = wp_remote_retrieve_body($response);
        $data = json_decode($body, true);

        if ($status_code >= 200 && $status_code < 300 && isset($data['id'])) {
            $erp_order_id = $data['id'];
            $order->update_meta_data(self::META_ERP_ORDER_ID, $erp_order_id);
            $order->update_meta_data(self::META_SYNC_STATUS, self::STATUS_SYNCED);
            $order->update_meta_data(self::META_SYNC_ERROR, '');
            $order->update_meta_data(self::META_SYNCED_AT, current_time('mysql'));
            $order->save();
            $order->add_order_note(sprintf(
                __('[V-ERP] Đồng bộ thành công. Mã ERP: %s', 'v-erp-woo-checkout'),
                $erp_order_id
            ));
        } else {
            $error_msg = isset($data['message']) ? $data['message'] : "HTTP $status_code";
            $order->update_meta_data(self::META_SYNC_STATUS, self::STATUS_FAILED);
            $order->update_meta_data(self::META_SYNC_ERROR, $error_msg);
            $order->save();
            $order->add_order_note(sprintf(
                __('[V-ERP] Đồng bộ thất bại (HTTP %d): %s', 'v-erp-woo-checkout'),
                $status_code,
                $error_msg
            ));
        }
    }

    public static function build_payload($order) {
        $items = [];
        foreach ($order->get_items() as $item) {
            $product = $item->get_product();
            $items[] = [
                'product_id' => (string) $item->get_product_id(),
                'variation_id' => (string) $item->get_variation_id(),
                'name' => $item->get_name(),
                'quantity' => $item->get_quantity(),
                'price' => (string) $order->get_item_total($item, false, false),
                'total' => (string) $order->get_item_total($item, false, true),
            ];
        }

        $coupons = [];
        foreach ($order->get_coupons() as $coupon) {
            $coupons[] = [
                'code' => $coupon->get_code(),
                'amount' => (string) $coupon->get_discount(),
            ];
        }

        $billing = $order->get_address('billing');
        $shipping = $order->get_address('shipping');

        return [
            'id' => (string) $order->get_id(),
            'number' => $order->get_order_number(),
            'status' => $order->get_status(),
            'currency' => $order->get_currency(),
            'total' => (string) $order->get_total(),
            'payment_method' => $order->get_payment_method(),
            'payment_method_title' => $order->get_payment_method_title(),
            'customer_note' => $order->get_customer_note(),
            'billing' => [
                'first_name' => $billing['first_name'] ?? '',
                'last_name' => $billing['last_name'] ?? '',
                'email' => $billing['email'] ?? '',
                'phone' => $billing['phone'] ?? '',
                'address_1' => $billing['address_1'] ?? '',
                'address_2' => $billing['address_2'] ?? '',
                'city' => $billing['city'] ?? '',
                'postcode' => $billing['postcode'] ?? '',
                'country' => $billing['country'] ?? '',
            ],
            'shipping' => [
                'first_name' => $shipping['first_name'] ?? ($billing['first_name'] ?? ''),
                'last_name' => $shipping['last_name'] ?? ($billing['last_name'] ?? ''),
                'address_1' => $shipping['address_1'] ?? ($billing['address_1'] ?? ''),
                'address_2' => $shipping['address_2'] ?? ($billing['address_2'] ?? ''),
                'city' => $shipping['city'] ?? ($billing['city'] ?? ''),
                'postcode' => $shipping['postcode'] ?? ($billing['postcode'] ?? ''),
                'country' => $shipping['country'] ?? ($billing['country'] ?? ''),
            ],
            'line_items' => $items,
            'coupon_lines' => $coupons,
        ];
    }

    public static function retry_order($order_id) {
        $order = wc_get_order($order_id);
        if (!$order) {
            return false;
        }
        self::submit_order($order);
        return true;
    }

    public static function get_sync_status_label($status) {
        $labels = [
            self::STATUS_SYNCED => 'Đã đồng bộ',
            self::STATUS_FAILED => 'Thất bại',
            self::STATUS_PENDING => 'Đang xử lý',
        ];
        return isset($labels[$status]) ? $labels[$status] : $status;
    }
}
