package base.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import base.entity.Product;
import base.security.CallerContext;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Parameters;

@ApplicationScoped
public class WooCommerceCatalogService {

    @Inject
    CallerContext caller;

    /**
     * Get changed products for the current tenant.
     */
    public CatalogChangesResult getChanges(Instant since, int limit, int offset) {
        String tenantCode = caller.tenantCode();
        
        StringBuilder query = new StringBuilder("tenantId = :tenantId and active = true");
        Parameters params = Parameters.with("tenantId", tenantCode);

        if (since != null) {
            query.append(" and updatedAt > :since");
            params.and("since", since);
        }

        long totalCount = Product.count(query.toString(), params);
        
        List<Product> items = Product.find(query.toString(), Sort.ascending("updatedAt"), params)
                .page(offset / limit, limit) // Panache uses page index, so offset / limit (assumes offset is a multiple of limit)
                .list();
                
        // For accurate pagination where offset might not be a clean multiple, we can use range:
        items = Product.find(query.toString(), Sort.ascending("updatedAt"), params)
                .range(offset, offset + limit - 1)
                .list();

        boolean hasMore = (offset + limit) < totalCount;

        return new CatalogChangesResult(items, totalCount, hasMore);
    }

    /**
     * Get summary of WooCommerce catalog sync.
     * MOCK: Returns mock data until Dev 1 finishes mapping table.
     */
    public CatalogSyncSummary getSummary() {
        String tenantCode = caller.tenantCode();
        
        // Mock data
        long totalProducts = Product.count("tenantId = ?1", tenantCode);
        long syncedProducts = Math.min(totalProducts, 20); // Mock 20 products synced
        Instant lastSyncTime = Instant.now().minusSeconds(3600); // Mock 1 hour ago

        return new CatalogSyncSummary(totalProducts, syncedProducts, lastSyncTime);
    }

    public static class CatalogChangesResult {
        public List<Product> items;
        public long totalCount;
        public boolean hasMore;

        public CatalogChangesResult(List<Product> items, long totalCount, boolean hasMore) {
            this.items = items;
            this.totalCount = totalCount;
            this.hasMore = hasMore;
        }
    }

    public static class CatalogSyncSummary {
        public long totalProducts;
        public long syncedProducts;
        public Instant lastSyncTime;

        public CatalogSyncSummary(long totalProducts, long syncedProducts, Instant lastSyncTime) {
            this.totalProducts = totalProducts;
            this.syncedProducts = syncedProducts;
            this.lastSyncTime = lastSyncTime;
        }
    }
}
