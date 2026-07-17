<?php
/**
 * Plugin Name: V-ERP WooCommerce Checkout
 * Description: Đồng bộ đơn hàng WooCommerce sang V-ERP và hiển thị trạng thái xử lý trên My Account
 * Version: 1.0.0
 * Requires Plugins: woocommerce
 */

defined('ABSPATH') or exit;

define('V_ERP_WC_CHECKOUT_VERSION', '1.0.0');
define('V_ERP_WC_CHECKOUT_PLUGIN_DIR', plugin_dir_path(__FILE__));

define('V_ERP_WC_DEFAULT_API_BASE', defined('V_ERP_API_BASE_URL') ? V_ERP_API_BASE_URL : 'http://host.docker.internal:8081/api');
define('V_ERP_WC_DEFAULT_SECRET', defined('WOOCOMMERCE_WEBHOOK_SECRET') ? WOOCOMMERCE_WEBHOOK_SECRET : '');

require_once V_ERP_WC_CHECKOUT_PLUGIN_DIR . 'includes/class-hmac.php';
require_once V_ERP_WC_CHECKOUT_PLUGIN_DIR . 'includes/class-settings.php';
require_once V_ERP_WC_CHECKOUT_PLUGIN_DIR . 'includes/class-order-handler.php';
require_once V_ERP_WC_CHECKOUT_PLUGIN_DIR . 'includes/class-status-sync.php';
require_once V_ERP_WC_CHECKOUT_PLUGIN_DIR . 'includes/class-admin.php';

add_action('plugins_loaded', 'v_erp_wc_checkout_init');
function v_erp_wc_checkout_init() {
    if (!class_exists('WooCommerce')) {
        return;
    }
    V_ERP_Settings::init();
    V_ERP_Order_Handler::init();
    V_ERP_Status_Sync::init();

    if (is_admin()) {
        V_ERP_Admin::init();
    }
}

register_activation_hook(__FILE__, 'v_erp_wc_checkout_activate');
function v_erp_wc_checkout_activate() {
    if (!class_exists('WooCommerce')) {
        deactivate_plugins(plugin_basename(__FILE__));
        wp_die('V-ERP WooCommerce Checkout yêu cầu WooCommerce được kích hoạt.');
    }
}
