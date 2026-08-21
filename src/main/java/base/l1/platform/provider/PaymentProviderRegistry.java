package base.l1.platform.provider;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a provider id to its implementation, and fails hard when it cannot.
 *
 * <p>There is deliberately no fallback. A mock that answers for an unrecognised provider is how you
 * discover months later that you never actually took the money.
 */
@ApplicationScoped
public class PaymentProviderRegistry {

    @Inject
    Instance<PaymentProvider> providers;

    private final Map<String, PaymentProvider> byId = new LinkedHashMap<>();

    @PostConstruct
    void index() {
        for (PaymentProvider provider : providers) {
            byId.put(provider.id().toLowerCase(Locale.ROOT), provider);
        }
    }

    public PaymentProvider require(String id) {
        if (id == null || id.isBlank()) {
            throw new UnknownProviderException("<blank>", known());
        }
        PaymentProvider provider = byId.get(id.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new UnknownProviderException(id, known());
        }
        return provider;
    }

    public Set<String> known() {
        return byId.keySet();
    }
}

