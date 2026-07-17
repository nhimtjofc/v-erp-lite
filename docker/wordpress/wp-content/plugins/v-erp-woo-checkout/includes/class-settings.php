<?php

defined('ABSPATH') or exit;

class V_ERP_Settings {

    const OPTION_TENANT_ID = 'v_erp_tenant_id';
    const OPTION_SALES_CHANNEL_ID = 'v_erp_sales_channel_id';
    const OPTION_API_BASE = 'v_erp_api_base_url';

    public static function init() {
        add_action('admin_init', [__CLASS__, 'register_settings']);
    }

    public static function register_settings() {
        register_setting('v_erp_settings', self::OPTION_TENANT_ID, [
            'type' => 'string',
            'sanitize_callback' => 'sanitize_text_field',
            'default' => 'v-erp-app',
        ]);
        register_setting('v_erp_settings', self::OPTION_SALES_CHANNEL_ID, [
            'type' => 'string',
            'sanitize_callback' => 'sanitize_text_field',
            'default' => '',
        ]);
        register_setting('v_erp_settings', self::OPTION_API_BASE, [
            'type' => 'string',
            'sanitize_callback' => 'esc_url_raw',
            'default' => V_ERP_WC_DEFAULT_API_BASE,
        ]);
    }

    public static function get_tenant_id() {
        return get_option(self::OPTION_TENANT_ID, 'v-erp-app');
    }

    public static function get_sales_channel_id() {
        return get_option(self::OPTION_SALES_CHANNEL_ID, '');
    }

    public static function get_api_base() {
        $base = get_option(self::OPTION_API_BASE, '');
        if (empty($base)) {
            $base = defined('V_ERP_API_BASE_URL') ? V_ERP_API_BASE_URL : 'http://host.docker.internal:8081/api';
        }
        return rtrim($base, '/');
    }
}
