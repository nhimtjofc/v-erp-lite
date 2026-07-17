<?php
if (!defined('ABSPATH')) exit;

class VERPC_ProductSynchronizer {

    private $api;
    private $results = [];
    private $mapping_items = [];

    public function __construct($api_client) {
        $this->api = $api_client;
    }

    /**
     * Run full catalog sync. Paginates through all changes,
     * upserts Woo products/variants, stores ERP IDs in meta,
     * reports mappings back to V-ERP.
     */
    public function run() {
        $this->results = [];
        $this->mapping_items = [];

        $since   = get_option('verpc_since', '');
        $limit   = 50;
        $offset  = 0;
        $has_more = true;

        while ($has_more) {
            $changes = $this->api->get_changes($since, $limit, $offset);
            if (is_wp_error($changes)) {
                $this->add_result('API', 'FAILED', $changes->get_error_message());
                break;
            }

            $items = $changes['items'] ?? [];
            if (empty($items)) break;

            foreach ($items as $dto) {
                try {
                    $this->sync_product($dto);
                } catch (Exception $e) {
                    $this->add_result($dto['sku'] ?? $dto['id'], 'FAILED', $e->getMessage());
                }
            }

            $has_more = !empty($changes['hasMore']);
            $offset  += $limit;
        }

        if (!empty($this->mapping_items)) {
            $map_res = $this->api->upsert_mappings($this->mapping_items);
            if (is_wp_error($map_res)) {
                $this->add_result('MAPPING', 'WARN', 'Report mappings failed: ' . $map_res->get_error_message());
            }
        }

        $now = gmdate('Y-m-d\TH:i:s\Z');
        update_option('verpc_last_sync_at', $now);
        update_option('verpc_since', $now);
        update_option('verpc_last_results', $this->results);

        return $this->results;
    }

    private function sync_product($dto) {
        $erp_id   = $dto['id'];
        $sku      = $dto['sku'];
        $woo_id   = $this->find_woo_product_by_erp_id($erp_id);
        $has_variants = !empty($dto['variants']);

        $args = [
            'name'          => $dto['name'],
            'sku'           => $sku,
            'description'   => wp_kses_post($dto['description'] ?? ''),
            'status'        => 'publish',
            'manage_stock'  => true,
            'stock_quantity'=> max(0, (int)($dto['availableQuantity'] ?? 0)),
            'regular_price' => $this->minor_to_price($dto['basePriceMinor']),
        ];

        if (isset($dto['salePriceMinor']) && $dto['salePriceMinor'] !== null) {
            $args['sale_price'] = $this->minor_to_price($dto['salePriceMinor']);
        }

        if ($has_variants) {
            $args['type'] = 'variable';
        }

        if ($woo_id) {
            $args['id'] = $woo_id;
            $product = wc_update_product($args);
        } else {
            $product = wc_create_product($args);
        }

        if (is_wp_error($product)) {
            throw new Exception('WooCommerce create/update failed: ' . $product->get_error_message());
        }

        $woo_id = $product->get_id();

        update_post_meta($woo_id, '_erp_product_id', $erp_id);

        $variant_mappings = [];
        if ($has_variants) {
            $variant_mappings = $this->sync_variants($woo_id, $dto['variants']);
        }

        $this->add_result($sku, 'SUCCESS', '', $erp_id, 'PRODUCT', (string)$woo_id);

        $this->mapping_items[] = [
            'erpId'            => $erp_id,
            'entityType'       => 'PRODUCT',
            'externalId'       => (string)$woo_id,
            'externalParentId' => '',
            'syncStatus'       => 'SUCCESS',
            'errorMessage'     => '',
        ];

        foreach ($variant_mappings as $vm) {
            $this->mapping_items[] = $vm;
        }
    }

    private function sync_variants($parent_woo_id, $variants) {
        $mappings = [];
        $parent = wc_get_product($parent_woo_id);
        if (!$parent) return $mappings;

        $attrs = $this->build_attributes($variants);
        $parent->set_attributes($attrs);
        $parent->save();

        foreach ($variants as $vdto) {
            $erp_v_id    = $vdto['id'];
            $existing_id = $this->find_woo_variation_by_erp_id($erp_v_id);

            if ($existing_id) {
                $variation = wc_get_product($existing_id);
                if (!$variation) continue;
            } else {
                $variation = new WC_Product_Variation();
                $variation->set_parent_id($parent_woo_id);
            }

            $variation->set_sku($vdto['sku']);
            $variation->set_regular_price($this->minor_to_price($vdto['priceMinor']));
            $variation->set_manage_stock(true);
            $variation->set_stock_quantity(max(0, (int)($vdto['availableQuantity'] ?? 0)));

            $attr_map = [];
            if (!empty($vdto['optionValues'])) {
                foreach ($vdto['optionValues'] as $name => $value) {
                    $attr_map['pa_' . sanitize_title($name)] = strtolower(sanitize_title($value));
                }
            }
            $variation->set_attributes($attr_map);

            $variation->update_meta_data('_erp_variant_id', $erp_v_id);
            $vid = $variation->save();

            $this->add_result($vdto['sku'], 'SUCCESS', '', $erp_v_id, 'PRODUCT_VARIANT', (string)$vid, (string)$parent_woo_id);

            $mappings[] = [
                'erpId'            => $erp_v_id,
                'entityType'       => 'PRODUCT_VARIANT',
                'externalId'       => (string)$vid,
                'externalParentId' => (string)$parent_woo_id,
                'syncStatus'       => 'SUCCESS',
                'errorMessage'     => '',
            ];
        }
        return $mappings;
    }

    private function build_attributes($variants) {
        $attr_map = [];
        foreach ($variants as $v) {
            if (empty($v['optionValues'])) continue;
            foreach ($v['optionValues'] as $name => $value) {
                $key = sanitize_title($name);
                if (!isset($attr_map[$key])) {
                    $attr_map[$key] = ['name' => $name, 'values' => []];
                }
                $attr_map[$key]['values'][sanitize_title($value)] = $value;
            }
        }
        $attrs = [];
        foreach ($attr_map as $info) {
            $attrs[] = [
                'name'      => $info['name'],
                'options'   => array_values($info['values']),
                'variation' => true,
                'visible'   => true,
            ];
        }
        return $attrs;
    }

    private function find_woo_product_by_erp_id($erp_id) {
        $products = wc_get_products([
            'limit'     => 1,
            'meta_key'  => '_erp_product_id',
            'meta_value'=> $erp_id,
        ]);
        return !empty($products) ? $products[0]->get_id() : null;
    }

    private function find_woo_variation_by_erp_id($erp_variant_id) {
        global $wpdb;
        $row = $wpdb->get_var($wpdb->prepare(
            "SELECT post_id FROM {$wpdb->postmeta} WHERE meta_key = '_erp_variant_id' AND meta_value = %s LIMIT 1",
            $erp_variant_id
        ));
        return $row ? (int)$row : null;
    }

    private function minor_to_price($minor) {
        if ($minor === null || $minor === 0) return '0.00';
        return number_format($minor / 100, 2, '.', '');
    }

    private function add_result($sku, $status, $error = '', $erp_id = null, $entity_type = 'PRODUCT', $external_id = '', $external_parent_id = '') {
        $this->results[] = [
            'sku'              => $sku ?? '',
            'status'           => $status,
            'error'            => $error,
            'erpId'            => $erp_id ?? '',
            'entityType'       => $entity_type,
            'externalId'       => $external_id,
            'externalParentId' => $external_parent_id,
        ];
    }
}
