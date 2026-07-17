<?php
if (!defined('ABSPATH')) exit;

class VERPC_Admin {

    public function init() {
        add_action('admin_menu', [$this, 'add_admin_menu']);
        add_action('admin_init', [$this, 'register_settings']);
        add_action('admin_enqueue_scripts', [$this, 'enqueue_assets']);
        add_action('wp_ajax_verpc_sync_now', [$this, 'ajax_sync_now']);
        add_action('wp_ajax_verpc_check_health', [$this, 'ajax_check_health']);
        add_action('verpc_hourly_sync', [$this, 'run_scheduled_sync']);
    }

    public function add_admin_menu() {
        add_menu_page(
            'V-ERP Catalog Sync',
            'V-ERP Catalog',
            'manage_options',
            'verpc-catalog-sync',
            [$this, 'render_page'],
            'dashicons-update',
            30
        );
    }

    public function register_settings() {
        register_setting('verpc_settings', 'verpc_api_base_url', ['type' => 'string', 'sanitize_callback' => 'esc_url_raw']);
        register_setting('verpc_settings', 'verpc_api_token',    ['type' => 'string', 'sanitize_callback' => 'sanitize_text_field']);
        register_setting('verpc_settings', 'verpc_business_id',  ['type' => 'string', 'sanitize_callback' => 'sanitize_text_field']);
    }

    public function enqueue_assets($hook) {
        if (strpos($hook, 'verpc-catalog-sync') === false) return;
        wp_enqueue_style('verpc-admin', VERPC_URL . 'assets/css/admin.css', [], VERPC_VERSION);
        wp_enqueue_script('verpc-admin', VERPC_URL . 'assets/js/admin.js', ['jquery'], VERPC_VERSION, true);
        wp_localize_script('verpc-admin', 'verpc_ajax', [
            'ajax_url' => admin_url('admin-ajax.php'),
            'nonce'    => wp_create_nonce('verpc_nonce'),
        ]);
    }

    public function render_page() {
        $api_url     = get_option('verpc_api_base_url', '');
        $api_token   = get_option('verpc_api_token', '');
        $business_id = get_option('verpc_business_id', '');
        $last_sync   = get_option('verpc_last_sync_at', '');
        $since       = get_option('verpc_since', '');
        $results     = get_option('verpc_last_results', []);
        if (!is_array($results)) $results = [];
        ?>
        <div class="wrap verpc-wrap">
            <h1>V-ERP Catalog Sync</h1>

            <div class="verpc-card">
                <h2>Connection</h2>
                <div class="verpc-card-body">
                    <p><span class="verpc-label">Status:</span>
                        <span id="verpc-health-indicator" class="verpc-status verpc-status-checking">Checking...</span>
                    </p>
                    <p><span class="verpc-label">Store URL:</span> <?php echo esc_html(get_option('siteurl')); ?></p>
                    <p><span class="verpc-label">Last check:</span> <span id="verpc-health-time">—</span></p>
                    <p><button type="button" class="button" id="verpc-check-health-btn">Check Health</button></p>
                </div>
            </div>

            <div class="verpc-card">
                <h2>Sync Summary</h2>
                <div class="verpc-card-body">
                    <p><span class="verpc-label">Last sync:</span> <span id="verpc-last-sync"><?php echo esc_html($last_sync ?: '—'); ?></span></p>
                    <p><span class="verpc-label">Since:</span> <span id="verpc-since"><?php echo esc_html($since ?: '—'); ?></span></p>
                    <p>
                        <button type="button" class="button button-primary" id="verpc-sync-now-btn">Sync Now</button>
                        <span id="verpc-sync-spinner" class="spinner" style="float:none;vertical-align:middle;"></span>
                    </p>
                </div>
            </div>

            <div class="verpc-card">
                <h2>Recent Results</h2>
                <div class="verpc-card-body">
                    <?php if (empty($results)): ?>
                        <p>No sync results yet.</p>
                    <?php else: ?>
                        <table class="widefat striped verpc-results-table">
                            <thead><tr>
                                <th>SKU</th><th>Type</th><th>Status</th><th>Error</th><th>ERP ID</th><th>Woo ID</th>
                            </tr></thead>
                            <tbody>
                                <?php foreach ($results as $r): ?>
                                <tr class="verpc-result-<?php echo esc_attr(strtolower($r['status'] ?? 'unknown')); ?>">
                                    <td><?php echo esc_html($r['sku'] ?? ''); ?></td>
                                    <td><?php echo esc_html($r['entityType'] ?? 'PRODUCT'); ?></td>
                                    <td><?php echo esc_html($r['status'] ?? ''); ?></td>
                                    <td><?php echo esc_html($r['error'] ?? ''); ?></td>
                                    <td><code><?php echo esc_html($r['erpId'] ?? ''); ?></code></td>
                                    <td><code><?php echo esc_html($r['externalId'] ?? ''); ?></code></td>
                                </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    <?php endif; ?>
                </div>
            </div>

            <div class="verpc-card">
                <h2>Settings</h2>
                <div class="verpc-card-body">
                    <form method="post" action="options.php">
                        <?php settings_fields('verpc_settings'); ?>
                        <table class="form-table">
                            <tr>
                                <th scope="row"><label for="verpc_api_base_url">V-ERP API URL</label></th>
                                <td><input type="url" id="verpc_api_base_url" name="verpc_api_base_url" value="<?php echo esc_attr($api_url); ?>" class="regular-text" />
                                    <p class="description">e.g. http://host.docker.internal:8081/api</p></td>
                            </tr>
                            <tr>
                                <th scope="row"><label for="verpc_api_token">API Token</label></th>
                                <td><input type="password" id="verpc_api_token" name="verpc_api_token" value="<?php echo esc_attr($api_token); ?>" class="regular-text" />
                                    <p class="description">JWT Bearer token for V-ERP API authentication</p></td>
                            </tr>
                            <tr>
                                <th scope="row"><label for="verpc_business_id">Business ID</label></th>
                                <td><input type="text" id="verpc_business_id" name="verpc_business_id" value="<?php echo esc_attr($business_id); ?>" class="regular-text" />
                                    <p class="description">UUID of the business in V-ERP</p></td>
                            </tr>
                        </table>
                        <?php submit_button(); ?>
                    </form>
                </div>
            </div>
        </div>
        <?php
    }

    public function ajax_sync_now() {
        check_ajax_referer('verpc_nonce');
        if (!current_user_can('manage_options')) wp_die('Forbidden');

        $api_url     = get_option('verpc_api_base_url', '');
        $api_token   = get_option('verpc_api_token', '');
        $business_id = get_option('verpc_business_id', '');
        if (empty($api_url) || empty($business_id)) {
            wp_send_json_error(['message' => 'API URL and Business ID required.']);
        }

        $api  = new VERPC_ApiClient($api_url, $business_id, $api_token);
        $sync = new VERPC_ProductSynchronizer($api);
        $results = $sync->run();

        $success = $failed = 0;
        foreach ($results as $r) {
            if ($r['status'] === 'SUCCESS') $success++;
            elseif ($r['status'] === 'FAILED') $failed++;
        }

        wp_send_json_success([
            'results'  => $results,
            'summary'  => "Synced: $success, Failed: $failed",
            'lastSync' => get_option('verpc_last_sync_at', ''),
        ]);
    }

    public function ajax_check_health() {
        check_ajax_referer('verpc_nonce');
        if (!current_user_can('manage_options')) wp_die('Forbidden');

        $api_url     = get_option('verpc_api_base_url', '');
        $api_token   = get_option('verpc_api_token', '');
        $business_id = get_option('verpc_business_id', '');
        if (empty($api_url) || empty($business_id)) {
            wp_send_json_error(['message' => 'API URL and Business ID required.']);
        }

        $api    = new VERPC_ApiClient($api_url, $business_id, $api_token);
        $health = $api->check_health();

        if (is_wp_error($health)) {
            wp_send_json_error(['message' => $health->get_error_message()]);
        }
        update_option('verpc_health_cache', $health);
        wp_send_json_success($health);
    }

    public function run_scheduled_sync() {
        $api_url     = get_option('verpc_api_base_url', '');
        $api_token   = get_option('verpc_api_token', '');
        $business_id = get_option('verpc_business_id', '');
        if (empty($api_url) || empty($business_id)) return;

        $api  = new VERPC_ApiClient($api_url, $business_id, $api_token);
        (new VERPC_ProductSynchronizer($api))->run();
    }
}
