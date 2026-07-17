<?php
if (!defined('ABSPATH')) exit;

class VERPC_Activator {

    public static function activate() {
        $defaults = [
            'verpc_api_base_url' => getenv('V_ERP_API_BASE_URL') ?: 'http://host.docker.internal:8081/api',
            'verpc_api_token'    => '',
            'verpc_business_id'  => '',
            'verpc_since'        => '',
            'verpc_last_sync_at' => '',
            'verpc_last_results' => [],
            'verpc_health_cache' => [],
        ];
        foreach ($defaults as $key => $val) {
            if (get_option($key) === false) {
                update_option($key, $val);
            }
        }
        if (!wp_next_scheduled('verpc_hourly_sync')) {
            wp_schedule_event(time(), 'hourly', 'verpc_hourly_sync');
        }
    }

    public static function deactivate() {
        $ts = wp_next_scheduled('verpc_hourly_sync');
        if ($ts) wp_unschedule_event($ts, 'verpc_hourly_sync');
    }
}
