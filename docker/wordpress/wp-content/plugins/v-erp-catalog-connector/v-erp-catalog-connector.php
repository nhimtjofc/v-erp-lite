<?php
/**
 * Plugin Name:       V-ERP Catalog Connector
 * Plugin URI:        https://v-erp.com
 * Description:       Pulls catalog changes from V-ERP API and upserts WooCommerce products/variations. Stores ERP IDs in Woo meta. Shows sync results in wp-admin.
 * Version:           1.0.0
 * Requires PHP:      8.2
 * Requires at least: 6.4
 * Author:            V-ERP Team
 * License:           GPL v2 or later
 * Text Domain:       v-erc
 */

if (!defined('ABSPATH')) exit;

define('VERPC_VERSION', '1.0.0');
define('VERPC_FILE', __FILE__);
define('VERPC_DIR', plugin_dir_path(__FILE__));
define('VERPC_URL', plugin_dir_url(__FILE__));

require_once VERPC_DIR . 'includes/class-activator.php';
require_once VERPC_DIR . 'includes/class-api-client.php';
require_once VERPC_DIR . 'includes/class-product-synchronizer.php';
require_once VERPC_DIR . 'includes/class-admin.php';

register_activation_hook(__FILE__, ['VERPC_Activator', 'activate']);
register_deactivation_hook(__FILE__, ['VERPC_Activator', 'deactivate']);

add_action('plugins_loaded', function () {
    (new VERPC_Admin())->init();
});
