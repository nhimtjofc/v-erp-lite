package base.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import base.entity.ExternalEntityMapping;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.SalesChannel;
import base.security.CallerContext;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

@ApplicationScoped
public class WooCommerceCatalogService {

    @Inject
    CallerContext caller;

    /**
     * Get changed products for the current tenant and business.
     */
    public CatalogChangesResult getChanges(UUID businessId, Instant since, int limit, int offset) {
        String tenantCode = caller.tenantCode();
        SalesChannel channel = getChannel(tenantCode, businessId);

        StringBuilder query = new StringBuilder("tenantId = :tenantId and active = true");
        Parameters params = Parameters.with("tenantId", tenantCode);

        if (since != null) {
            query.append(" and updatedAt > :since");
            params.and("since", since);
        }

        long totalCount = Product.count(query.toString(), params);
        
        List<Product> products = Product.find(query.toString(), Sort.ascending("updatedAt"), params)
                .range(offset, offset + limit - 1)
                .list();

        List<CatalogProductDTO> items = new ArrayList<>();
        for (Product p : products) {
            CatalogProductDTO dto = new CatalogProductDTO();
            dto.id = p.getId();
            dto.sku = p.getSku();
            dto.name = p.getName();
            dto.description = p.getDescription();
            dto.basePriceMinor = p.getBasePriceMinor();
            dto.salePriceMinor = p.getSalePriceMinor();
            dto.currency = p.getCurrency();
            dto.availableQuantity = p.getAvailableQuantity();
            
            ExternalEntityMapping pMap = ExternalEntityMapping.findByErpId(tenantCode, channel.getId(), "PRODUCT", p.getId());
            if (pMap != null) {
                dto.externalId = pMap.getExternalEntityId();
            }

            for (ProductVariant v : p.getVariants()) {
                CatalogVariantDTO vDto = new CatalogVariantDTO();
                vDto.id = v.getId();
                vDto.sku = v.getSku();
                vDto.priceMinor = v.getPriceMinor();
                vDto.availableQuantity = v.getAvailableQuantity();
                vDto.optionValues = v.getOptionValues();
                
                ExternalEntityMapping vMap = ExternalEntityMapping.findByErpId(tenantCode, channel.getId(), "PRODUCT_VARIANT", v.getId());
                if (vMap != null) {
                    vDto.externalId = vMap.getExternalEntityId();
                }
                dto.variants.add(vDto);
            }
            items.add(dto);
        }

        boolean hasMore = (offset + limit) < totalCount;
        return new CatalogChangesResult(items, totalCount, hasMore);
    }

    /**
     * Get summary of WooCommerce catalog sync.
     */
    public CatalogSyncSummary getSummary(UUID businessId) {
        String tenantCode = caller.tenantCode();
        SalesChannel channel = getChannel(tenantCode, businessId);
        
        long totalProducts = Product.count("tenantId = ?1 and active = true", tenantCode);
        long syncedProducts = ExternalEntityMapping.count("tenantId = ?1 and salesChannelId = ?2 and entityType = 'PRODUCT' and syncStatus = 'SUCCESS'", tenantCode, channel.getId());
        
        // Find latest sync time
        ExternalEntityMapping latest = ExternalEntityMapping.find("tenantId = ?1 and salesChannelId = ?2", Sort.descending("lastSyncedAt"), tenantCode, channel.getId()).firstResult();
        Instant lastSyncTime = latest != null ? latest.getLastSyncedAt() : null;

        return new CatalogSyncSummary(totalProducts, syncedProducts, lastSyncTime);
    }

    @Transactional
    public void upsertMappings(UUID businessId, List<CatalogMappingItem> items) {
        String tenantCode = caller.tenantCode();
        SalesChannel channel = getChannel(tenantCode, businessId);
        Instant now = Instant.now();

        for (CatalogMappingItem item : items) {
            ExternalEntityMapping mapping = ExternalEntityMapping.findByErpId(tenantCode, channel.getId(), item.entityType, item.erpId);
            if (mapping == null) {
                mapping = new ExternalEntityMapping();
                mapping.setId(UUID.randomUUID());
                mapping.setTenantId(tenantCode);
                mapping.setSalesChannelId(channel.getId());
                mapping.setEntityType(item.entityType);
                mapping.setErpEntityId(item.erpId);
            }
            mapping.setExternalEntityId(item.externalId);
            mapping.setExternalParentId(item.externalParentId);
            mapping.setSyncStatus(item.syncStatus);
            mapping.setLastErrorMessage(item.errorMessage);
            mapping.setLastAttemptedAt(now);
            if ("SUCCESS".equals(item.syncStatus)) {
                mapping.setLastSyncedAt(now);
            }
            mapping.persist();
        }
    }

    private SalesChannel getChannel(String tenantCode, UUID businessId) {
        SalesChannel channel;
        if (businessId != null) {
            channel = SalesChannel.findWooCommerce(tenantCode, businessId);
        } else {
            List<SalesChannel> channels = SalesChannel.listWooCommerce(tenantCode);
            channel = channels.isEmpty() ? null : channels.getFirst();
        }
        if (channel == null) {
            throw new jakarta.ws.rs.NotFoundException("WooCommerce sales channel not found");
        }
        return channel;
    }

    public static class CatalogProductDTO {
        public UUID id;
        public String sku;
        public String name;
        public String description;
        public long basePriceMinor;
        public Long salePriceMinor;
        public String currency;
        public int availableQuantity;
        public String externalId;
        public List<CatalogVariantDTO> variants = new ArrayList<>();
    }

    public static class CatalogVariantDTO {
        public UUID id;
        public String sku;
        public long priceMinor;
        public int availableQuantity;
        public String externalId;
        public Map<String, String> optionValues;
    }

    public static class CatalogChangesResult {
        public List<CatalogProductDTO> items;
        public long totalCount;
        public boolean hasMore;

        public CatalogChangesResult(List<CatalogProductDTO> items, long totalCount, boolean hasMore) {
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

    public static class CatalogMappingSyncRequest {
        public List<CatalogMappingItem> items = new ArrayList<>();
    }

    public static class CatalogMappingItem {
        public UUID erpId;
        public String entityType; // PRODUCT or PRODUCT_VARIANT
        public String externalId;
        public String externalParentId;
        public String syncStatus;
        public String errorMessage;
    }
}
