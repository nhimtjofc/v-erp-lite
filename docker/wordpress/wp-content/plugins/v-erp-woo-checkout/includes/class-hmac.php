<?php

defined('ABSPATH') or exit;

class V_ERP_HMAC {

    public static function sign($payload, $secret) {
        return base64_encode(
            hash_hmac('sha256', $payload, $secret, true)
        );
    }

    public static function verify($payload, $signature, $secret) {
        $expected = self::sign($payload, $secret);
        return hash_equals($expected, $signature);
    }

    public static function get_secret() {
        $secret = defined('WOOCOMMERCE_WEBHOOK_SECRET') ? WOOCOMMERCE_WEBHOOK_SECRET : '';
        if (empty($secret)) {
            $secret = get_option('v_erp_webhook_secret', '');
        }
        return $secret;
    }

    public static function is_configured() {
        return !empty(self::get_secret());
    }
}
