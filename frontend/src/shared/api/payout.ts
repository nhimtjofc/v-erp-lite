import { useMutation, useQuery } from '@tanstack/react-query'
import { approve, list, reject, status } from './gen/sdk.gen'
import type { PayoutResponse, PayoutStatusResponse } from './gen/types.gen'

export type PayoutRequest = PayoutResponse
export type PayoutAutomationStatus = PayoutStatusResponse

export interface PayoutActionInput {
  id: string
  reason?: string
}

export const PAYOUT_STATUS_META: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' }
> = {
  AUTO_APPROVED: { label: 'Auto approved', variant: 'secondary' },
  FLAGGED: { label: 'Needs review', variant: 'destructive' },
  APPROVED: { label: 'Approved', variant: 'secondary' },
  REJECTED: { label: 'Rejected', variant: 'destructive' },
  PAID: { label: 'Paid', variant: 'default' },
}

export function useSellerPayoutsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['seller-payouts', walletId],
    queryFn: async () => {
      const response = await list({ query: { walletId }, throwOnError: true })
      return response.data ?? []
    },
    enabled: !!walletId,
  })
}

export function usePayoutStatusQuery(walletId?: string) {
  return useQuery({
    queryKey: ['payout-status', walletId],
    queryFn: async () => {
      const response = await status({ query: { walletId }, throwOnError: true })
      return response.data
    },
    enabled: !!walletId,
  })
}

/** Backend always returns only FLAGGED requests for an admin caller. */
export function useAdminPayoutsQuery() {
  return useQuery({
    queryKey: ['admin-payouts', 'FLAGGED'],
    queryFn: async () => {
      const response = await list({ throwOnError: true })
      return response.data ?? []
    },
  })
}

export function useApprovePayoutMutation() {
  return useMutation({
    mutationFn: async ({ id }: PayoutActionInput) => {
      const response = await approve({ path: { id }, throwOnError: true })
      return response.data
    },
  })
}

export function useRejectPayoutMutation() {
  return useMutation({
    mutationFn: async ({ id, reason }: PayoutActionInput) => {
      const response = await reject({ path: { id }, body: { reason }, throwOnError: true })
      return response.data
    },
  })
}
