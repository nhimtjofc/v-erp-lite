import { useMutation, useQuery } from '@tanstack/react-query'

/**
 * Payout (seller withdrawal) data layer — reads the live backend
 * (`PayoutResource`: `POST /payouts`, `GET /payouts`, `POST /payouts/{id}/approve`,
 * `POST /payouts/{id}/reject`).
 */

/** Mirror of `PayoutResource.PayoutResponse`. */
export interface PayoutRequest {
  id: string
  walletId: string
  amountMinor: number
  currency: string
  /** PENDING, APPROVED, REJECTED. */
  status: string
  bankName: string
  bankAccountNumber: string
  bankAccountHolder: string
  /** Admin's approve/reject note, sent with the action. */
  reason?: string | null
  createdAt?: string
  updatedAt?: string
}

/** Mirror of `PayoutResource.CreatePayoutRequest`. */
export interface CreatePayoutInput {
  walletId: string
  amountMinor: number
  currency: string
  bankName: string
  bankAccountNumber: string
  bankAccountHolder: string
}

/** Payload for an admin approve/reject action. `reason` is optional for approve, expected for reject. */
export interface PayoutActionInput {
  id: string
  reason?: string
}

/** Vietnamese label + badge styling for each payout status (shared by seller & admin views). */
export const PAYOUT_STATUS_META: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' }
> = {
  PENDING: { label: 'Chờ duyệt', variant: 'secondary' },
  APPROVED: { label: 'Đã duyệt', variant: 'default' },
  REJECTED: { label: 'Từ chối', variant: 'destructive' },
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  const token = localStorage.getItem('access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE}/api${path}`, { ...init, headers })
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    }
    if (response.status === 403) {
      throw new Error('Bạn không có quyền thực hiện thao tác này.')
    }
    throw new Error((await response.text()) || `Yêu cầu thất bại (${response.status})`)
  }
  return response.json() as Promise<T>
}

function fetchPayoutsApi(walletId: string): Promise<PayoutRequest[]> {
  return apiRequest<PayoutRequest[]>(`/payouts?walletId=${encodeURIComponent(walletId)}`)
}

function createPayoutApi(input: CreatePayoutInput): Promise<PayoutRequest> {
  return apiRequest<PayoutRequest>('/payouts', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** Admin view: list payouts across all sellers, optionally filtered by status. */
function fetchAdminPayoutsApi(status?: string): Promise<PayoutRequest[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<PayoutRequest[]>(`/payouts${qs}`)
}

function approvePayoutApi(input: PayoutActionInput): Promise<PayoutRequest> {
  return apiRequest<PayoutRequest>(`/payouts/${input.id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ reason: input.reason ?? null }),
  })
}

function rejectPayoutApi(input: PayoutActionInput): Promise<PayoutRequest> {
  return apiRequest<PayoutRequest>(`/payouts/${input.id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason: input.reason ?? null }),
  })
}

// ---------------------------------------------------------------------------
// React Query hooks
// ---------------------------------------------------------------------------

export function useSellerPayoutsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['seller-payouts', walletId],
    queryFn: () => fetchPayoutsApi(walletId!),
    enabled: !!walletId,
  })
}

/** Admin: pending (or status-filtered) payout requests across all sellers. */
export function useAdminPayoutsQuery(status?: string) {
  return useQuery({
    queryKey: ['admin-payouts', status ?? 'ALL'],
    queryFn: () => fetchAdminPayoutsApi(status),
  })
}

export function useApprovePayoutMutation() {
  return useMutation({
    mutationFn: (input: PayoutActionInput) => approvePayoutApi(input),
  })
}

export function useRejectPayoutMutation() {
  return useMutation({
    mutationFn: (input: PayoutActionInput) => rejectPayoutApi(input),
  })
}

export function useCreatePayoutMutation() {
  return useMutation({
    mutationFn: (input: CreatePayoutInput) => createPayoutApi(input),
  })
}
