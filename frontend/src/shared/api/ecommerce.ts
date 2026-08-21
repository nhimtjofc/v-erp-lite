import { useQuery, useMutation } from '@tanstack/react-query'
import { client } from './gen/client.gen'
import type { EcommerceOrderDto } from './gen'

export interface EcommerceHealthResponse {
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

async function getEcommerceHealth(businessId?: string): Promise<EcommerceHealthResponse> {
  const response = await client.get<{ 200: EcommerceHealthResponse }, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/ecommerce/v1/health',
    query: businessId ? { businessId } : undefined,
  })
  return response.data
}

export function useEcommerceHealthQuery(
  tenantScope: string | undefined,
  businessId?: string,
  options?: { enabled?: boolean }
) {
  return useQuery({
    queryKey: ['ecommerce', 'health', tenantScope ?? 'unresolved', businessId ?? 'default'],
    queryFn: () => getEcommerceHealth(businessId),
    enabled: (options?.enabled ?? true) && Boolean(tenantScope),
    retry: 1,
    staleTime: 0,
  })
}

async function getEcommerceOrders(query?: {
  salesChannelId?: string
  syncStatus?: string
  search?: string
  limit?: number
  offset?: number
}): Promise<EcommerceOrderDto[]> {
  const response = await client.get<{ 200: EcommerceOrderDto[] }, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/ecommerce/v1/orders',
    query,
  })
  return response.data
}

export function useEcommerceOrdersQuery(
  tenantScope: string | undefined,
  query?: {
    salesChannelId?: string
    syncStatus?: string
    search?: string
    limit?: number
    offset?: number
  },
  options?: { enabled?: boolean }
) {
  return useQuery({
    queryKey: ['ecommerce', 'orders', tenantScope ?? 'unresolved', query],
    queryFn: () => getEcommerceOrders(query),
    enabled: (options?.enabled ?? true) && Boolean(tenantScope),
    retry: 1,
    staleTime: 0,
  })
}

async function reconcileEcommerceOrder(params: {
  externalId: string
  salesChannelId?: string
  body?: string
}): Promise<any> {
  const response = await client.post<any, unknown, true>({
    security: [{ scheme: 'bearer', type: 'http' }],
    throwOnError: true,
    url: '/api/integrations/ecommerce/v1/orders/{externalId}/reconcile',
    path: { externalId: params.externalId },
    query: params.salesChannelId ? { salesChannelId: params.salesChannelId } : undefined,
    body: params.body ?? '',
  })
  return response.data
}

export function useReconcileEcommerceOrderMutation() {
  return useMutation({
    mutationFn: reconcileEcommerceOrder,
  })
}
