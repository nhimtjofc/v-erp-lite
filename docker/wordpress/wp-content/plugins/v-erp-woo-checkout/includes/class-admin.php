<?php

defined('ABSPATH') or exit;

class V_ERP_Admin {

    public static function init() {
        add_action('admin_menu', [__CLASS__, 'add_admin_menu']);
        add_action('admin_enqueue_scripts', [__CLASS__, 'enqueue_styles']);
        add_action('wp_ajax_v_erp_retry_order', [__CLASS__, 'ajax_retry_order']);
        add_action('wp_ajax_v_erp_health_check', [__CLASS__, 'ajax_health_check']);
    }

    public static function add_admin_menu() {
        add_menu_page(
            'V-ERP WooCommerce',
            'V-ERP Woo',
            'manage_options',
            'v-erp-woo',
            [__CLASS__, 'render_health_page'],
            'dashicons-update',
            56
        );

        add_submenu_page(
            'v-erp-woo',
            'Cài đặt',
            'Cài đặt',
            'manage_options',
            'v-erp-woo-settings',
            [__CLASS__, 'render_settings_page']
        );

        add_submenu_page(
            'v-erp-woo',
            'Nhật ký đồng bộ',
            'Nhật ký đồng bộ',
            'manage_options',
            'v-erp-woo-sync-log',
            [__CLASS__, 'render_sync_log_page']
        );

        add_submenu_page(
            'v-erp-woo',
            'Kiểm tra kết nối',
            'Health',
            'manage_options',
            'v-erp-woo-health',
            [__CLASS__, 'render_health_page']
        );
    }

    public static function enqueue_styles($hook) {
        if (strpos($hook, 'v-erp-woo') === false) {
            return;
        }
        wp_enqueue_style(
            'v-erp-woo-admin',
            plugin_dir_url(dirname(__FILE__)) . 'assets/admin.css',
            [],
            V_ERP_WC_CHECKOUT_VERSION
        );
    }

    public static function render_health_page() {
        $api_base = V_ERP_Settings::get_api_base();
        $tenant_id = V_ERP_Settings::get_tenant_id();
        $channel_id = V_ERP_Settings::get_sales_channel_id();
        $secret_ok = V_ERP_HMAC::is_configured();
        ?>
        <div class="wrap v-erp-admin">
            <h1>V-ERP WooCommerce — Kiểm tra kết nối</h1>

            <div class="v-erp-health-grid">
                <div class="v-erp-card">
                    <h3>Cấu hình</h3>
                    <table class="form-table">
                        <tr><th>API Base URL</th><td><code><?php echo esc_html($api_base); ?></code></td></tr>
                        <tr><th>Tenant ID</th><td><code><?php echo esc_html($tenant_id); ?></code></td></tr>
                        <tr><th>Sales Channel ID</th><td><code><?php echo esc_html($channel_id ?: '(chưa cấu hình)'); ?></code></td></tr>
                        <tr><th>Webhook Secret</th><td><?php echo $secret_ok ? '<span class="v-erp-ok">Đã cấu hình</span>' : '<span class="v-erp-err">Chưa cấu hình</span>'; ?></td></tr>
                    </table>
                </div>

                <div class="v-erp-card">
                    <h3>Kết quả kiểm tra</h3>
                    <div id="v-erp-health-result">
                        <p><?php esc_html_e('Bấm "Kiểm tra" để kiểm tra kết nối đến V-ERP.', 'v-erp-woo-checkout'); ?></p>
                    </div>
                    <p>
                        <button class="button button-primary" id="v-erp-health-btn" onclick="vErpRunHealthCheck()">
                            <?php esc_html_e('Kiểm tra', 'v-erp-woo-checkout'); ?>
                        </button>
                    </p>
                </div>
            </div>

            <script>
            function vErpRunHealthCheck() {
                var btn = document.getElementById('v-erp-health-btn');
                var result = document.getElementById('v-erp-health-result');
                btn.disabled = true;
                btn.textContent = 'Đang kiểm tra...';
                result.innerHTML = '<p>Đang gọi V-ERP health endpoint...</p>';

                var data = new FormData();
                data.append('action', 'v_erp_health_check');
                data.append('_ajax_nonce', '<?php echo wp_create_nonce('v_erp_health_check'); ?>');

                fetch(ajaxurl, { method: 'POST', body: data })
                    .then(function(r) { return r.json(); })
                    .then(function(resp) {
                        if (resp.success && resp.data) {
                            var d = resp.data;
                            var html = '<table class="form-table">';
                            html += '<tr><th>Trạng thái kết nối</th><td><strong>' + (d.connectionStatus || 'N/A') + '</strong></td></tr>';
                            html += '<tr><th>Health</th><td>' + (d.healthStatus || 'N/A') + '</td></tr>';
                            html += '<tr><th>Store URL</th><td>' + (d.storeUrl || 'N/A') + '</td></tr>';
                            html += '<tr><th>Secret đã cấu hình</th><td>' + (d.secretConfigured ? 'Có' : 'Không') + '</td></tr>';
                            html += '<tr><th>Số mapping</th><td>' + (d.mappedEntityCount || 0) + '</td></tr>';
                            html += '<tr><th>Số webhook event</th><td>' + (d.webhookEventCount || 0) + '</td></tr>';
                            html += '<tr><th>Lần kiểm tra cuối</th><td>' + (d.lastCheckedAt || '') + '</td></tr>';
                            html += '<tr><th>Lần kết nối cuối</th><td>' + (d.lastConnectedAt || '') + '</td></tr>';
                            if (d.message) { html += '<tr><th>Thông báo</th><td>' + escHtml(d.message) + '</td></tr>'; }
                            html += '</table>';
                            result.innerHTML = html;
                        } else {
                            result.innerHTML = '<p class="v-erp-err">Lỗi: ' + escHtml(resp.data || 'Unknown error') + '</p>';
                        }
                        btn.disabled = false;
                        btn.textContent = 'Kiểm tra lại';
                    })
                    .catch(function(err) {
                        result.innerHTML = '<p class="v-erp-err">Lỗi kết nối: ' + escHtml(err.message) + '</p>';
                        btn.disabled = false;
                        btn.textContent = 'Kiểm tra lại';
                    });
            }

            function escHtml(str) {
                if (!str) return '';
                return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
            }
            </script>
        </div>
        <?php
    }

    public static function render_settings_page() {
        if (isset($_POST['submit'])) {
            check_admin_referer('v_erp_settings');
            update_option(V_ERP_Settings::OPTION_TENANT_ID, sanitize_text_field($_POST['v_erp_tenant_id'] ?? ''));
            update_option(V_ERP_Settings::OPTION_SALES_CHANNEL_ID, sanitize_text_field($_POST['v_erp_sales_channel_id'] ?? ''));
            update_option(V_ERP_Settings::OPTION_API_BASE, esc_url_raw($_POST['v_erp_api_base_url'] ?? ''));
            echo '<div class="notice notice-success"><p>Đã lưu cài đặt.</p></div>';
        }

        $tenant_id = V_ERP_Settings::get_tenant_id();
        $channel_id = V_ERP_Settings::get_sales_channel_id();
        $api_base = V_ERP_Settings::get_api_base();
        $secret_ok = V_ERP_HMAC::is_configured();
        ?>
        <div class="wrap v-erp-admin">
            <h1>V-ERP WooCommerce — Cài đặt</h1>
            <form method="post">
                <?php wp_nonce_field('v_erp_settings'); ?>
                <table class="form-table">
                    <tr>
                        <th><label for="v_erp_api_base_url">API Base URL</label></th>
                        <td>
                            <input type="url" id="v_erp_api_base_url" name="v_erp_api_base_url"
                                value="<?php echo esc_attr($api_base); ?>" class="regular-text" />
                            <p class="description">VD: <code>http://host.docker.internal:8081/api</code></p>
                        </td>
                    </tr>
                    <tr>
                        <th><label for="v_erp_tenant_id">Tenant ID</label></th>
                        <td>
                            <input type="text" id="v_erp_tenant_id" name="v_erp_tenant_id"
                                value="<?php echo esc_attr($tenant_id); ?>" class="regular-text" />
                            <p class="description">VD: <code>v-erp-app</code></p>
                        </td>
                    </tr>
                    <tr>
                        <th><label for="v_erp_sales_channel_id">Sales Channel ID</label></th>
                        <td>
                            <input type="text" id="v_erp_sales_channel_id" name="v_erp_sales_channel_id"
                                value="<?php echo esc_attr($channel_id); ?>" class="regular-text" />
                            <p class="description">UUID của WooCommerce sales channel trong V-ERP.</p>
                        </td>
                    </tr>
                    <tr>
                        <th>Webhook Secret</th>
                        <td>
                            <?php echo $secret_ok
                                ? '<span class="v-erp-ok">Đã cấu hình (từ environment/hằng số)</span>'
                                : '<span class="v-erp-err">Chưa cấu hình — đặt hằng số <code>WOOCOMMERCE_WEBHOOK_SECRET</code> hoặc biến môi trường cùng tên.</span>'; ?>
                        </td>
                    </tr>
                </table>
                <?php submit_button(); ?>
            </form>
        </div>
        <?php
    }

    public static function render_sync_log_page() {
        $status_filter = isset($_GET['sync_status']) ? $_GET['sync_status'] : '';
        ?>
        <div class="wrap v-erp-admin">
            <h1>V-ERP WooCommerce — Nhật ký đồng bộ</h1>

            <form method="get" style="margin-bottom: 1em;">
                <input type="hidden" name="page" value="v-erp-woo-sync-log" />
                <select name="sync_status">
                    <option value="">Tất cả</option>
                    <option value="synced" <?php selected($status_filter, 'synced'); ?>>Đã đồng bộ</option>
                    <option value="failed" <?php selected($status_filter, 'failed'); ?>>Thất bại</option>
                    <option value="pending" <?php selected($status_filter, 'pending'); ?>>Đang xử lý</option>
                </select>
                <?php submit_button('Lọc', 'secondary', '', false); ?>
            </form>

            <table class="wp-list-table widefat fixed striped">
                <thead>
                    <tr>
                        <th>Woo Order ID</th>
                        <th>ERP Order ID</th>
                        <th>Sync Status</th>
                        <th>Error</th>
                        <th>Last Attempt</th>
                        <th>Thao tác</th>
                    </tr>
                </thead>
                <tbody>
                    <?php
                    $orders = wc_get_orders([
                        'limit' => 50,
                        'orderby' => 'date',
                        'order' => 'DESC',
                        'meta_key' => V_ERP_Order_Handler::META_ERP_ORDER_ID,
                        'meta_compare' => 'EXISTS',
                    ]);

                    if (!empty($status_filter)) {
                        $orders = array_filter($orders, function($o) use ($status_filter) {
                            return $o->get_meta(V_ERP_Order_Handler::META_SYNC_STATUS) === $status_filter;
                        });
                    }

                    if (empty($orders)): ?>
                        <tr><td colspan="6">Không có đơn hàng nào được đồng bộ.</td></tr>
                    <?php else:
                        foreach ($orders as $order):
                            $sync_status = $order->get_meta(V_ERP_Order_Handler::META_SYNC_STATUS);
                            $erp_id = $order->get_meta(V_ERP_Order_Handler::META_ERP_ORDER_ID);
                            $error = $order->get_meta(V_ERP_Order_Handler::META_SYNC_ERROR);
                            $last_attempt = $order->get_meta(V_ERP_Order_Handler::META_LAST_ATTEMPT);
                    ?>
                        <tr>
                            <td>
                                <a href="<?php echo esc_url($order->get_edit_order_url()); ?>" target="_blank">
                                    #<?php echo esc_html($order->get_id()); ?>
                                </a>
                            </td>
                            <td><?php echo esc_html($erp_id ?: '—'); ?></td>
                            <td><?php echo esc_html(V_ERP_Order_Handler::get_sync_status_label($sync_status)); ?></td>
                            <td><?php echo esc_html($error ?: '—'); ?></td>
                            <td><?php echo esc_html($last_attempt ?: '—'); ?></td>
                            <td>
                                <?php if ($sync_status === V_ERP_Order_Handler::STATUS_FAILED): ?>
                                    <button class="button button-small v-erp-retry-btn"
                                        data-order-id="<?php echo esc_attr($order->get_id()); ?>">
                                        Retry
                                    </button>
                                <?php endif; ?>
                            </td>
                        </tr>
                    <?php endforeach;
                    endif; ?>
                </tbody>
            </table>
        </div>

        <script>
        jQuery(document).on('click', '.v-erp-retry-btn', function() {
            var btn = jQuery(this);
            var orderId = btn.data('order-id');
            btn.prop('disabled', true).text('Đang retry...');

            var data = {
                action: 'v_erp_retry_order',
                order_id: orderId,
                _ajax_nonce: '<?php echo wp_create_nonce('v_erp_retry_order'); ?>'
            };

            jQuery.post(ajaxurl, data, function(resp) {
                if (resp.success) {
                    btn.text('Đã retry');
                    location.reload();
                } else {
                    btn.text('Retry thất bại');
                    alert('Retry thất bại: ' + (resp.data || 'Unknown error'));
                    btn.prop('disabled', false).text('Retry');
                }
            }).fail(function() {
                btn.text('Lỗi kết nối');
                btn.prop('disabled', false).text('Retry');
            });
        });
        </script>
        <?php
    }

    public static function ajax_retry_order() {
        check_ajax_referer('v_erp_retry_order');

        if (!current_user_can('manage_options')) {
            wp_send_json_error('Không có quyền');
        }

        $order_id = intval($_POST['order_id'] ?? 0);
        if ($order_id <= 0) {
            wp_send_json_error('Invalid order ID');
        }

        V_ERP_Order_Handler::retry_order($order_id);
        wp_send_json_success('Retry initiated');
    }

    public static function ajax_health_check() {
        check_ajax_referer('v_erp_health_check');

        if (!current_user_can('manage_options')) {
            wp_send_json_error('Không có quyền');
        }

        $api_base = V_ERP_Settings::get_api_base();
        $tenant_id = V_ERP_Settings::get_tenant_id();
        $channel_id = V_ERP_Settings::get_sales_channel_id();

        if (empty($channel_id)) {
            wp_send_json_error('Chưa cấu hình Sales Channel ID');
        }

        $endpoint = $api_base . '/integrations/woocommerce/v1/health?businessId=' . urlencode($channel_id);

        $response = wp_remote_get($endpoint, [
            'headers' => ['Accept' => 'application/json'],
            'timeout' => 10,
        ]);

        if (is_wp_error($response)) {
            wp_send_json_error($response->get_error_message());
        }

        $body = wp_remote_retrieve_body($response);
        $data = json_decode($body, true);

        if (!is_array($data)) {
            wp_send_json_error('Invalid response from V-ERP');
        }

        // Strip sensitive fields
        unset($data['secretReference']);
        wp_send_json_success($data);
    }
}
