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

export interface SubscriptionUpgradeQuote {
  fromPlanCode: string
  toPlanCode: string
  toPlanName: string
  billingPeriod: 'MONTHLY' | 'ANNUAL'
  remainingDays: number
  amountMinor: number
  currency: string
  currentPeriodEndsAt: string
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
  upgradeQuote?: SubscriptionUpgradeQuote
  features: Record<string, boolean>
  limits: Record<string, number>
}

export interface ChangeTenantSubscriptionInput {
  tenantCode: string
  planCode: string
  billingPeriod: 'MONTHLY' | 'ANNUAL'
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED'
}

export interface SubscriptionPayment {
  id: string
  tenantId: string
  planCode: string
  billingPeriod: 'MONTHLY' | 'ANNUAL'
  amountMinor: number
  currency: string
  status: 'PENDING' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'FAILED'
  paymentReference?: string
  submittedAt?: string
  reviewedAt?: string
  rejectionReason?: string
  createdAt?: string
}

export interface SubscriptionFinancialStats {
  recognizedProfitMinor: number
  currentMonthProfitMinor: number
  pendingRevenueMinor: number
  approvedPaymentCount: number
  activeSubscriptionCount: number
  baseSubscriptionCount: number
  premiumSubscriptionCount: number
  currency: string
  profitBasis: 'APPROVED_SUBSCRIPTION_PAYMENTS'
}

export type SubscriptionMockScenario = 'EXPIRE_NOW' | 'RESET_PERIOD'

export interface CreateSubscriptionPaymentInput {
  planCode: string
  billingPeriod: 'MONTHLY' | 'ANNUAL'
  idempotencyKey: string
}

export const MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY = ['my-subscription-payments'] as const
export const ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY = ['admin-subscription-payments'] as const
export const SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY = ['subscription-financial-stats'] as const

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
    staleTime: 0,
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

export function useMockSubscriptionTimeMutation() {
  return useMutation({
    mutationFn: async ({ tenantCode, scenario }: { tenantCode: string; scenario: SubscriptionMockScenario }) => {
      const response = await client.post<TenantEntitlements, unknown, true>({
        url: '/api/admin/tenants/{tenantCode}/subscription/mock-time',
        path: { tenantCode },
        body: { scenario },
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
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

export function useMySubscriptionPaymentsQuery() {
  return useQuery({
    queryKey: MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY,
    queryFn: async () => {
      const response = await client.get<SubscriptionPayment[], unknown, true>({
        url: '/api/subscriptions/payments', security: bearerSecurity, throwOnError: true,
      })
      return response.data ?? []
    },
  })
}

export function useCreateSubscriptionPaymentMutation() {
  return useMutation({
    mutationFn: async (input: CreateSubscriptionPaymentInput) => {
      const response = await client.post<SubscriptionPayment, unknown, true>({
        url: '/api/subscriptions/payments', body: input, security: bearerSecurity, throwOnError: true,
      })
      return response.data
    },
  })
}

export function useMockSubscriptionPaymentWebhookMutation() {
  return useMutation({
    mutationFn: async ({ id, gateway, status }: { id: string; gateway: 'VIETQR'; status: 'SUCCESS' | 'FAILED' }) => {
      const response = await client.post<SubscriptionPayment, unknown, true>({
        url: '/api/subscriptions/payments/{id}/mock-webhook',
        path: { id },
        body: { gateway, status },
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
  })
}

export function useSubscriptionFinancialStatsQuery() {
  return useQuery({
    queryKey: SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY,
    queryFn: async () => {
      const response = await client.get<SubscriptionFinancialStats, unknown, true>({
        url: '/api/admin/subscription-payments/stats',
        security: bearerSecurity,
        throwOnError: true,
      })
      return response.data
    },
  })
}

export function useAdminSubscriptionPaymentsQuery(status?: SubscriptionPayment['status']) {
  return useQuery({
    queryKey: [...ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY, status ?? 'ALL'],
    queryFn: async () => {
      const response = await client.get<SubscriptionPayment[], unknown, true>({
        url: '/api/admin/subscription-payments', query: status ? { status } : undefined, security: bearerSecurity, throwOnError: true,
      })
      return response.data ?? []
    },
  })
}

export function useApproveSubscriptionPaymentMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await client.post<SubscriptionPayment, unknown, true>({
        url: '/api/admin/subscription-payments/{id}/approve', path: { id }, security: bearerSecurity, throwOnError: true,
      })
      return response.data
    },
  })
}

export function useRejectSubscriptionPaymentMutation() {
  return useMutation({
    mutationFn: async ({ id, rejectionReason }: { id: string; rejectionReason: string }) => {
      const response = await client.post<SubscriptionPayment, unknown, true>({
        url: '/api/admin/subscription-payments/{id}/reject', path: { id }, body: { rejectionReason }, security: bearerSecurity, throwOnError: true,
      })
      return response.data
    },
  })
}
