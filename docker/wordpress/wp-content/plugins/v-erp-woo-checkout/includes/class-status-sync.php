<?php

defined('ABSPATH') or exit;

class V_ERP_Status_Sync {

    public static function init() {
        add_action('woocommerce_view_order', [__CLASS__, 'display_fulfillment_status'], 10, 1);
    }

    public static function display_fulfillment_status($order_id) {
        $order = wc_get_order($order_id);
        if (!$order) {
            return;
        }

        $sync_status = $order->get_meta(V_ERP_Order_Handler::META_SYNC_STATUS);
        $erp_order_id = $order->get_meta(V_ERP_Order_Handler::META_ERP_ORDER_ID);

        if (empty($erp_order_id)) {
            return;
        }

        $erp_status = self::fetch_erp_status($order_id);
        ?>
        <section class="v-erp-fulfillment">
            <h2><?php esc_html_e('Thông tin từ V-ERP', 'v-erp-woo-checkout'); ?></h2>
            <table class="woocommerce-table woocommerce-table--order-details shop_table order_details">
                <tbody>
                    <tr>
                        <th><?php esc_html_e('Đồng bộ ERP', 'v-erp-woo-checkout'); ?></th>
                        <td><?php echo esc_html(V_ERP_Order_Handler::get_sync_status_label($sync_status)); ?></td>
                    </tr>
                    <?php if ($erp_status): ?>
                        <tr>
                            <th><?php esc_html_e('Trạng thái xử lý', 'v-erp-woo-checkout'); ?></th>
                            <td><?php echo esc_html(self::status_label($erp_status['status'] ?? '')); ?></td>
                        </tr>
                        <tr>
                            <th><?php esc_html_e('Mã đơn ERP', 'v-erp-woo-checkout'); ?></th>
                            <td><?php echo esc_html($erp_status['orderNumber'] ?? $erp_order_id); ?></td>
                        </tr>
                        <?php if (!empty($erp_status['updatedAt'])): ?>
                            <tr>
                                <th><?php esc_html_e('Cập nhật lúc', 'v-erp-woo-checkout'); ?></th>
                                <td><?php echo esc_html($erp_status['updatedAt']); ?></td>
                            </tr>
                        <?php endif; ?>
                        <?php if (!empty($erp_status['items'])): ?>
                            <tr>
                                <th><?php esc_html_e('Sản phẩm', 'v-erp-woo-checkout'); ?></th>
                                <td>
                                    <?php foreach ($erp_status['items'] as $item): ?>
                                        <div><?php echo esc_html($item['productName'] ?? ''); ?> × <?php echo esc_html($item['quantity'] ?? 0); ?></div>
                                    <?php endforeach; ?>
                                </td>
                            </tr>
                        <?php endif; ?>
                    <?php else: ?>
                        <tr>
                            <th><?php esc_html_e('Trạng thái xử lý', 'v-erp-woo-checkout'); ?></th>
                            <td><?php esc_html_e('Đang chờ dữ liệu từ ERP...', 'v-erp-woo-checkout'); ?></td>
                        </tr>
                    <?php endif; ?>
                    <?php
                    $error = $order->get_meta(V_ERP_Order_Handler::META_SYNC_ERROR);
                    if (!empty($error) && $sync_status === V_ERP_Order_Handler::STATUS_FAILED): ?>
                        <tr>
                            <th><?php esc_html_e('Lỗi đồng bộ', 'v-erp-woo-checkout'); ?></th>
                            <td style="color: #cc0000;"><?php echo esc_html($error); ?></td>
                        </tr>
                    <?php endif; ?>
                </tbody>
            </table>
        </section>
        <?php
    }

    public static function fetch_erp_status($woo_order_id) {
        $secret = V_ERP_HMAC::get_secret();
        if (empty($secret)) {
            return null;
        }

        $tenant_id = V_ERP_Settings::get_tenant_id();
        $channel_id = V_ERP_Settings::get_sales_channel_id();
        $api_base = V_ERP_Settings::get_api_base();

        $endpoint = $api_base . '/integrations/woocommerce/v1/orders/'
            . urlencode((string) $woo_order_id)
            . '/status'
            . '?tenantId=' . urlencode($tenant_id)
            . '&salesChannelId=' . urlencode($channel_id);

        $signature = V_ERP_HMAC::sign('', $secret);

        $response = wp_remote_get($endpoint, [
            'headers' => [
                'Accept' => 'application/json',
                'X-WC-Webhook-Signature' => $signature,
            ],
            'timeout' => 15,
        ]);

        if (is_wp_error($response)) {
            return null;
        }

        $status_code = wp_remote_retrieve_response_code($response);
        if ($status_code !== 200) {
            return null;
        }

        $body = wp_remote_retrieve_body($response);
        $data = json_decode($body, true);

        if (!is_array($data) || !isset($data['status'])) {
            return null;
        }

        return $data;
    }

    public static function status_label($erp_status) {
        $labels = [
            'PENDING' => 'Chờ xác nhận',
            'CONFIRMED' => 'Đã xác nhận',
            'SHIPPED' => 'Đã bàn giao vận chuyển',
            'COMPLETED' => 'Hoàn tất',
            'CANCELLED' => 'Đã hủy',
        ];
        $label = isset($labels[$erp_status]) ? $labels[$erp_status] : $erp_status;
        return '<span class="v-erp-status-badge v-erp-status-' . strtolower($erp_status) . '">' . esc_html($label) . '</span>';
    }
}
