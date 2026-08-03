import { useMutation, useQuery } from '@tanstack/react-query'
import { client } from './gen/client.gen'

export interface SubscriptionPlan {
  code: string
  name: string
  productSlice: string
  monthlyPriceMinor: number
  annualPriceMinor: number
  currency: string
  features: Record<string, boolean>
  limits: Record<string, number>
  active: boolean
}

export interface TenantEntitlements {
  planCode: string
  planName: string
  productSlice: string
  status: string
  billingPeriod: string
  priceMinor: number
  currency: string
  currentPeriodEndsAt?: string
  readOnly: boolean
  features: Record<string, boolean>
  limits: Record<string, number>
}

export interface ChangeTenantSubscriptionInput {
  tenantCode: string
  planCode: string
  billingPeriod: 'MONTHLY' | 'ANNUAL'
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED'
}

const bearerSecurity = [{ scheme: 'bearer' as const, type: 'http' as const }]

export function subscriptionQueryKey(tenantCode: string) {
  return ['tenant-subscription', tenantCode] as const
}

export function useSubscriptionPlansQuery() {
  return useQuery({
    queryKey: ['subscription-plans'],
    queryFn: async () => {
      const response = await client.get<SubscriptionPlan[], unknown, true>({
        url: '/api/subscriptions/plans',
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data ?? []
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useMySubscriptionQuery() {
  return useQuery({
    queryKey: ['my-subscription'],
    queryFn: async () => {
      const response = await client.get<TenantEntitlements, unknown, true>({
        url: '/api/subscriptions/me',
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
    staleTime: 60 * 1000,
  })
}
export function useAdminTenantSubscriptionQuery(tenantCode?: string) {
  return useQuery({
    queryKey: subscriptionQueryKey(tenantCode ?? ''),
    queryFn: async () => {
      const response = await client.get<TenantEntitlements, unknown, true>({
        url: '/api/admin/tenants/{tenantCode}/subscription',
        path: { tenantCode: tenantCode! },
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
    enabled: Boolean(tenantCode),
  })
}

export function useChangeTenantSubscriptionMutation() {
  return useMutation({
    mutationFn: async (input: ChangeTenantSubscriptionInput) => {
      const response = await client.put<TenantEntitlements, unknown, true>({
        url: '/api/admin/tenants/{tenantCode}/subscription',
        path: { tenantCode: input.tenantCode },
        body: {
          planCode: input.planCode,
          billingPeriod: input.billingPeriod,
          status: input.status,
        },
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
  })
}
