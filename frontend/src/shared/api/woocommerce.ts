import { useQuery, useMutation } from '@tanstack/react-query'
import { client } from './gen/client.gen'
import type { WooCommerceOrderDto } from './gen'

export interface WooCommerceHealthResponse {
  channelId: string | null
  businessId: string | null
  storeUrl: string | null
  connectionStatus: string
  healthStatus: string
  secretConfigured: boolean
  mappedEntityCount: number
  webhookEventCount: number
  lastCheckedAt: string | null
  lastConnectedAt: string | null
  message: string | null
}

async function getWooCommerceHealth(): Promise<WooCommerceHealthResponse> {
  const response = await client.get<{ 200: WooCommerceHealthResponse }, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/woocommerce/v1/health',
  })
  return response.data
}

export function useWooCommerceHealthQuery(tenantScope: string | undefined) {
  return useQuery({
    queryKey: ['woocommerce', 'health', tenantScope ?? 'unresolved'],
    queryFn: getWooCommerceHealth,
    enabled: Boolean(tenantScope),
    retry: 1,
    staleTime: 0,
  })
}

async function getWooCommerceOrders(query?: {
  salesChannelId?: string
  syncStatus?: string
  search?: string
  limit?: number
  offset?: number
}): Promise<WooCommerceOrderDto[]> {
  const response = await client.get<{ 200: WooCommerceOrderDto[] }, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/woocommerce/v1/orders',
    query,
  })
  return response.data
}

export function useWooCommerceOrdersQuery(
  tenantScope: string | undefined,
  query?: {
    salesChannelId?: string
    syncStatus?: string
    search?: string
    limit?: number
    offset?: number
  }
) {
  return useQuery({
    queryKey: ['woocommerce', 'orders', tenantScope ?? 'unresolved', query],
    queryFn: () => getWooCommerceOrders(query),
    enabled: Boolean(tenantScope),
    retry: 1,
    staleTime: 0,
  })
}

async function reconcileWooCommerceOrder(params: {
  externalId: string
  salesChannelId?: string
  body?: string
}): Promise<any> {
  const response = await client.post<any, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/woocommerce/v1/orders/{externalId}/reconcile',
    path: { externalId: params.externalId },
    query: params.salesChannelId ? { salesChannelId: params.salesChannelId } : undefined,
    body: params.body ?? '',
  })
  return response.data
}

export function useReconcileWooCommerceOrderMutation() {
  return useMutation({
    mutationFn: reconcileWooCommerceOrder,
  })
}
