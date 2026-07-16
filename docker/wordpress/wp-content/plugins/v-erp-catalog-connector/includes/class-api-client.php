<?php
if (!defined('ABSPATH')) exit;

class VERPC_ApiClient {

    private $base_url;
    private $business_id;
    private $token;

    public function __construct($base_url, $business_id, $token = '') {
        $this->base_url    = rtrim($base_url, '/');
        $this->business_id = $business_id;
        $this->token       = $token;
    }

    private function headers() {
        $h = ['Accept' => 'application/json'];
        if (!empty($this->token)) {
            $h['Authorization'] = 'Bearer ' . $this->token;
        }
        return $h;
    }

    private function build_url($path, $params = []) {
        $params['businessId'] = $this->business_id;
        return $this->base_url . $path . '?' . http_build_query($params);
    }

    /** GET /health — returns array or WP_Error */
    public function check_health() {
        $res = wp_remote_get($this->build_url('/integrations/woocommerce/v1/health'), [
            'timeout' => 15, 'headers' => $this->headers()
        ]);
        if (is_wp_error($res)) return $res;
        $code = wp_remote_retrieve_response_code($res);
        if ($code < 200 || $code >= 300) {
            return new WP_Error('http_error', "Health HTTP $code");
        }
        return json_decode(wp_remote_retrieve_body($res), true) ?: [];
    }

    /**
     * GET /catalog/changes — ISO-8601 UTC since + pagination.
     * Returns { items, totalCount, hasMore } or WP_Error.
     */
    public function get_changes($since, $limit = 50, $offset = 0) {
        $params = ['limit' => $limit, 'offset' => $offset];
        if (!empty($since)) $params['since'] = $since;
        $res = wp_remote_get($this->build_url('/integrations/woocommerce/v1/catalog/changes', $params), [
            'timeout' => 30, 'headers' => $this->headers()
        ]);
        if (is_wp_error($res)) return $res;
        $code = wp_remote_retrieve_response_code($res);
        if ($code < 200 || $code >= 300) {
            return new WP_Error('http_error', "GET /changes HTTP $code");
        }
        return json_decode(wp_remote_retrieve_body($res), true);
    }

    /** GET /catalog/summary */
    public function get_summary() {
        $res = wp_remote_get($this->build_url('/integrations/woocommerce/v1/catalog/summary'), [
            'timeout' => 15, 'headers' => $this->headers()
        ]);
        if (is_wp_error($res)) return $res;
        return json_decode(wp_remote_retrieve_body($res), true);
    }

    /**
     * POST /catalog/mappings — report synced Woo IDs back to V-ERP.
     */
    public function upsert_mappings($items) {
        $res = wp_remote_post($this->build_url('/integrations/woocommerce/v1/catalog/mappings'), [
            'timeout' => 30,
            'headers' => array_merge($this->headers(), ['Content-Type' => 'application/json']),
            'body'    => json_encode(['items' => $items]),
        ]);
        if (is_wp_error($res)) return $res;
        $code = wp_remote_retrieve_response_code($res);
        if ($code < 200 || $code >= 300) {
            return new WP_Error('http_error', "POST /mappings HTTP $code");
        }
        return true;
    }
}
