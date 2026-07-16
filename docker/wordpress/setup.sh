#!/bin/sh
set -eu

cd /var/www/html

max_attempts="${WORDPRESS_SETUP_MAX_ATTEMPTS:-60}"
attempt=0
until [ -f wp-includes/version.php ] && wp core version >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge "$max_attempts" ]; then
        echo "WordPress files were not ready before the setup timeout." >&2
        exit 1
    fi
    sleep 2
done

attempt=0
until wp db check >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge "$max_attempts" ]; then
        echo "WordPress database was not ready before the setup timeout." >&2
        exit 1
    fi
    sleep 2
done

if ! wp core is-installed >/dev/null 2>&1; then
    wp core install \
        --url="${WORDPRESS_URL}" \
        --title="${WORDPRESS_TITLE}" \
        --admin_user="${WORDPRESS_ADMIN_USER}" \
        --admin_password="${WORDPRESS_ADMIN_PASSWORD}" \
        --admin_email="${WORDPRESS_ADMIN_EMAIL}" \
        --skip-email
fi

if ! wp plugin is-installed woocommerce >/dev/null 2>&1; then
    wp plugin install woocommerce --version="${WOOCOMMERCE_VERSION}"
elif [ "$(wp plugin get woocommerce --field=version)" != "${WOOCOMMERCE_VERSION}" ]; then
    wp plugin install woocommerce --version="${WOOCOMMERCE_VERSION}" --force
fi

wp plugin activate woocommerce
wp plugin is-active woocommerce

if [ -d wp-content/plugins/v-erp-catalog-connector ]; then
    wp plugin activate v-erp-catalog-connector
    wp plugin is-active v-erp-catalog-connector
fi
