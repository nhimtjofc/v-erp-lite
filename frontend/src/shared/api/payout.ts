import { useMutation, useQuery } from '@tanstack/react-query'

/**
 * Payout (seller withdrawal) data layer.
 *
 * Day 3 (Dev 5): the payout request form ships against a mock so the seller can
 * submit a withdrawal without blocking on the backend. The real endpoints
 * already exist (`PayoutResource`: `POST /payouts`, `GET /payouts`) — flip
 * `VITE_WALLET_API=real` to hit them once the seller→business→wallet linkage
 * lands (planned for Week 2, Day 3).
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
  /**
   * Admin's approve/reject note. Captured client-side for now — the backend
   * `approve`/`reject` endpoints don't persist a reason yet (pending a column
   * from Dev 2), so this is only populated in mock mode.
   */
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

export const payoutDataSource: 'mock' | 'api' =
  import.meta.env.VITE_WALLET_API === 'real' ? 'api' : 'mock'

// ---------------------------------------------------------------------------
// Real API
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
// Mock
// ---------------------------------------------------------------------------

const MOCK_PAYOUTS: PayoutRequest[] = [
  {
    id: 'mock-payout-0002',
    walletId: 'mock-wallet-0001',
    amountMinor: 2_000_000,
    currency: 'VND',
    status: 'APPROVED',
    bankName: 'Vietcombank',
    bankAccountNumber: '0123456789',
    bankAccountHolder: 'NGUYEN VAN A',
    createdAt: '2026-07-03T09:00:00Z',
    updatedAt: '2026-07-03T15:00:00Z',
  },
  {
    id: 'mock-payout-0001',
    walletId: 'mock-wallet-0001',
    amountMinor: 500_000,
    currency: 'VND',
    status: 'PENDING',
    bankName: 'Techcombank',
    bankAccountNumber: '9988776655',
    bankAccountHolder: 'NGUYEN VAN A',
    createdAt: '2026-07-06T11:30:00Z',
    updatedAt: '2026-07-06T11:30:00Z',
  },
  // Payouts from other sellers — so the admin view shows a realistic queue.
  {
    id: 'mock-payout-0003',
    walletId: 'mock-wallet-0002',
    amountMinor: 3_200_000,
    currency: 'VND',
    status: 'PENDING',
    bankName: 'ACB',
    bankAccountNumber: '2233445566',
    bankAccountHolder: 'TRAN THI B',
    createdAt: '2026-07-08T08:15:00Z',
    updatedAt: '2026-07-08T08:15:00Z',
  },
  {
    id: 'mock-payout-0004',
    walletId: 'mock-wallet-0003',
    amountMinor: 1_250_000,
    currency: 'VND',
    status: 'PENDING',
    bankName: 'BIDV',
    bankAccountNumber: '7788990011',
    bankAccountHolder: 'LE VAN C',
    createdAt: '2026-07-09T02:40:00Z',
    updatedAt: '2026-07-09T02:40:00Z',
  },
  {
    id: 'mock-payout-0000',
    walletId: 'mock-wallet-0002',
    amountMinor: 800_000,
    currency: 'VND',
    status: 'REJECTED',
    bankName: 'MB Bank',
    bankAccountNumber: '5566778899',
    bankAccountHolder: 'TRAN THI B',
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T14:20:00Z',
  },
]

const mockDelay = () => new Promise((resolve) => setTimeout(resolve, 350))

async function fetchPayoutsMock(): Promise<PayoutRequest[]> {
  await mockDelay()
  // Newest first, matching the backend ordering.
  return [...MOCK_PAYOUTS].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
}

async function fetchAdminPayoutsMock(status?: string): Promise<PayoutRequest[]> {
  await mockDelay()
  const all = [...MOCK_PAYOUTS].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  return status ? all.filter((p) => p.status === status) : all
}

/** Mock approve/reject: transitions a PENDING payout in place, mirroring the backend state guard. */
async function transitionPayoutMock(
  id: string,
  status: 'APPROVED' | 'REJECTED',
  reason?: string,
): Promise<PayoutRequest> {
  await mockDelay()
  const payout = MOCK_PAYOUTS.find((p) => p.id === id)
  if (!payout) throw new Error('Không tìm thấy yêu cầu rút tiền')
  if (payout.status !== 'PENDING') {
    throw new Error('Chỉ xử lý được yêu cầu đang chờ duyệt')
  }
  payout.status = status
  payout.reason = reason?.trim() || null
  payout.updatedAt = new Date().toISOString()
  return { ...payout }
}

async function createPayoutMock(input: CreatePayoutInput): Promise<PayoutRequest> {
  await mockDelay()
  const now = new Date().toISOString()
  const payout: PayoutRequest = {
    id: `mock-payout-${Date.now()}`,
    status: 'PENDING',
    createdAt: now,
    updatedAt: now,
    ...input,
  }
  MOCK_PAYOUTS.push(payout)
  return payout
}

// ---------------------------------------------------------------------------
// React Query hooks (same shape regardless of data source)
// ---------------------------------------------------------------------------

export function useSellerPayoutsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['seller-payouts', payoutDataSource, walletId],
    queryFn: () =>
      payoutDataSource === 'api' ? fetchPayoutsApi(walletId!) : fetchPayoutsMock(),
    enabled: !!walletId,
  })
}

/** Admin: pending (or status-filtered) payout requests across all sellers. */
export function useAdminPayoutsQuery(status?: string) {
  return useQuery({
    queryKey: ['admin-payouts', payoutDataSource, status ?? 'ALL'],
    queryFn: () =>
      payoutDataSource === 'api'
        ? fetchAdminPayoutsApi(status)
        : fetchAdminPayoutsMock(status),
  })
}

export function useApprovePayoutMutation() {
  return useMutation({
    mutationFn: (input: PayoutActionInput) =>
      payoutDataSource === 'api'
        ? approvePayoutApi(input)
        : transitionPayoutMock(input.id, 'APPROVED', input.reason),
  })
}

export function useRejectPayoutMutation() {
  return useMutation({
    mutationFn: (input: PayoutActionInput) =>
      payoutDataSource === 'api'
        ? rejectPayoutApi(input)
        : transitionPayoutMock(input.id, 'REJECTED', input.reason),
  })
}

export function useCreatePayoutMutation() {
  return useMutation({
    mutationFn: (input: CreatePayoutInput) =>
      payoutDataSource === 'api' ? createPayoutApi(input) : createPayoutMock(input),
  })
}
