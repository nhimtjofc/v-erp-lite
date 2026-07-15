import { useQuery } from '@tanstack/react-query'
import { client } from './gen/client.gen'

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
