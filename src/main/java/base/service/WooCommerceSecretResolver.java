package base.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class WooCommerceSecretResolver {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    @Inject
    Config config;

    public boolean isConfigured(String secretReference) {
        return resolveSecret(secretReference).isPresent();
    }

    public Optional<String> resolveSecret(String secretReference) {
        if (secretReference == null || !SAFE_REFERENCE.matcher(secretReference).matches()) {
            return Optional.empty();
        }

        return nonBlank(
                config.getOptionalValue(
                        "woocommerce.webhook.secrets." + secretReference,
                        String.class
                )
        );
    }

    private Optional<String> nonBlank(Optional<String> value) {
        return value.filter(secret -> !secret.isBlank());
    }
}
