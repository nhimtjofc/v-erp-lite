package base.service;

import base.entity.TenantConfig;
import base.entity.UserSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.*;

/**
 * Manages tenant-level configuration and user-level preference overrides.
 * <p>
 * Resolution order: hardcoded defaults &larr; tenant config &larr; user settings.
 * Each level deep-merges over the previous — so a user can override just their
 * timezone while inheriting everything else from the tenant.
 */
@ApplicationScoped
public class TenantConfigService {

    // ── hardcoded defaults ──

    private static final Map<String, Object> DEFAULTS = defaults();

    private static Map<String, Object> defaults() {
        Map<String, Object> regional = new LinkedHashMap<>();
        regional.put("timezone", "UTC");
        regional.put("language", "vi");
        regional.put("dateFormat", "YYYY-MM-DD");
        regional.put("timeFormat", "24h");
        regional.put("firstDayOfWeek", "monday");

        Map<String, Object> currency = new LinkedHashMap<>();
        currency.put("code", "VND");
        currency.put("symbol", "₫");
        currency.put("decimalPlaces", 0);

        Map<String, Object> security = new LinkedHashMap<>();
        security.put("passwordMinLength", 8);
        security.put("passwordRequireSpecialChar", false);
        security.put("passwordRequireNumber", false);
        security.put("passwordRequireUppercase", false);
        security.put("mfaRequired", false);
        security.put("sessionTimeoutMinutes", 60);
        security.put("maxLoginAttempts", 5);
        security.put("lockoutDurationMinutes", 30);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("enableInventory", false);
        features.put("enableBilling", false);
        features.put("enableCrm", false);
        features.put("enableNotifications", true);

        Map<String, Object> communication = new LinkedHashMap<>();
        communication.put("senderName", "");
        communication.put("senderEmail", "");

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("regional", regional);
        defaults.put("currency", currency);
        defaults.put("security", security);
        defaults.put("features", features);
        defaults.put("communication", communication);

        return defaults;
    }

    /**
     * Returns a deep copy of the hardcoded defaults.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDefaults() {
        return (Map<String, Object>) deepCopy(DEFAULTS);
    }

    // ── tenant config ──

    /**
     * Returns the tenant-level config, or empty map if none saved.
     */
    public Map<String, Object> getTenantSettings(String tenantCode) {
        TenantConfig config = TenantConfig.find("tenantId", tenantCode).firstResult();
        return withVndCurrency(config != null ? config.getSettings() : new HashMap<>());
    }

    /**
     * Saves or replaces the tenant-level settings.
     */
    @Transactional
    public void putTenantSettings(String tenantCode, Map<String, Object> settings, UUID updatedBy) {
        Map<String, Object> normalizedSettings = withVndCurrency(settings);
        TenantConfig config = TenantConfig.find("tenantId", tenantCode).firstResult();
        if (config == null) {
            config = new TenantConfig();
            config.setTenantId(tenantCode);
            config.setSettings(normalizedSettings);
            config.setUpdatedBy(updatedBy);
            config.persist();  // only for new
        } else {
            config.setSettings(normalizedSettings);
            config.setUpdatedBy(updatedBy);
            // managed entity — no persist needed, flush on tx commit
        }
    }

    // ── user overrides ──

    /**
     * Returns the user's override settings, or empty map if none saved.
     */
    public Map<String, Object> getUserSettings(UUID userId) {
        UserSettings us = UserSettings.findByUserId(userId);
        return withVndCurrency(us != null ? us.getSettings() : new HashMap<>());
    }

    /**
     * Saves or replaces the user's override settings.
     */
    @Transactional
    public void putUserSettings(UUID userId, String tenantCode, Map<String, Object> settings) {
        Map<String, Object> normalizedSettings = withVndCurrency(settings);
        UserSettings us = UserSettings.findByUserId(userId);
        if (us == null) {
            us = new UserSettings();
            us.setUserId(userId);
            us.setTenantId(tenantCode);
            us.setSettings(normalizedSettings);
            us.persist();  // only for new
        } else {
            us.setSettings(normalizedSettings);
            // managed entity — no persist needed
        }
    }

    // ── effective (merged) ──

    /**
     * Returns the effective settings for a user by deep-merging:
     * defaults &larr; tenant config &larr; user overrides.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getEffectiveSettings(String tenantCode, UUID userId) {
        Map<String, Object> result = (Map<String, Object>) deepCopy(DEFAULTS);
        deepMerge(result, getTenantSettings(tenantCode));
        deepMerge(result, getUserSettings(userId));
        Map<String, Object> mergedCurrency = result.get("currency") instanceof Map
                ? (Map<String, Object>) result.get("currency")
                : null;
        result.put("currency", vndCurrencySettings(mergedCurrency));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> withVndCurrency(Map<String, Object> settings) {
        Map<String, Object> normalized = settings == null
                ? new LinkedHashMap<>()
                : (Map<String, Object>) deepCopy(settings);
        Map<String, Object> existingCurrency = normalized.get("currency") instanceof Map
                ? (Map<String, Object>) normalized.get("currency")
                : null;
        normalized.put("currency", vndCurrencySettings(existingCurrency));
        return normalized;
    }

    private static Map<String, Object> vndCurrencySettings() {
        return vndCurrencySettings(null);
    }

    private static Map<String, Object> vndCurrencySettings(Map<String, Object> existingCurrency) {
        Map<String, Object> currency = new LinkedHashMap<>();
        currency.put("code", "VND");
        currency.put("symbol", "₫");
        currency.put("decimalPlaces", 0);
        if (existingCurrency != null && existingCurrency.get("fiscalYearStart") instanceof String fys) {
            currency.put("fiscalYearStart", fys);
        }
        return currency;
    }

    // ── deep merge helpers ──

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map && target.get(key) instanceof Map) {
                deepMerge((Map<String, Object>) target.get(key), (Map<String, Object>) value);
            } else if (value != null) {
                target.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                copy.put(e.getKey(), deepCopy(e.getValue()));
            }
            return copy;
        }
        return value; // primitives, strings, etc. are immutable
    }
}
