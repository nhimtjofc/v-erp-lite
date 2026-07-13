import { useQuery } from '@tanstack/react-query'

/**
 * Seller wallet data layer — reads the live backend (`WalletResource`:
 * `GET /wallets/me`, `GET /wallets/{id}/transactions`).
 *
 * Mapping the logged-in seller to their wallet is isolated in
 * `resolveSellerWalletApi` below — the single seam to touch if that resolution
 * ever changes; nothing else in the dashboard does.
 */

export interface SellerWallet {
  id: string
  tenantId?: string
  ownerType?: string
  ownerId?: string
  availableBalanceMinor: number
  pendingBalanceMinor: number
  currency: string
  createdAt?: string
  updatedAt?: string
}

/** Mirror of `WalletResource.WalletTransactionResponse`. */
export interface WalletTransaction {
  id: string
  walletId: string
  amountMinor: number
  /** Ledger direction: money in vs money out. */
  type: 'CREDIT' | 'DEBIT' | string
  /** Business event: DEPOSIT, COMMISSION, ESCROW_HOLD, ESCROW_RELEASE, ESCROW_REFUND, PAYOUT, WITHDRAWAL. */
  transactionType: string
  referenceType?: string | null
  referenceId?: string | null
  sequenceNo: number
  description?: string | null
  createdAt?: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

/** How many ledger entries to pull for the history table (endpoint defaults to 20). */
const TRANSACTIONS_PAGE_SIZE = 50

async function apiRequest<T>(path: string): Promise<T> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  const token = localStorage.getItem('access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE}/api${path}`, { headers })
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    }
    if (response.status === 403) {
      throw new Error('Bạn không có quyền truy cập ví này.')
    }
    throw new Error((await response.text()) || `Yêu cầu thất bại (${response.status})`)
  }
  return response.json() as Promise<T>
}

/**
 * SEAM — the ONLY place that maps the logged-in seller to their wallet.
 *
 *   1. GET /wallets/me                               ← preferred (ACTIVE): the backend
 *        resolves user → business (business.created_by) → that business's SELLER wallet.
 *   2. VITE_SELLER_WALLET_ID pins a wallet           ← optional override for demos/testing.
 */
async function resolveSellerWalletApi(): Promise<SellerWallet | null> {
  // Strategy 2: explicit override, handy for demoing a specific seller's wallet.
  const pinnedId = import.meta.env.VITE_SELLER_WALLET_ID as string | undefined
  if (pinnedId) {
    return apiRequest<SellerWallet>(`/wallets/${pinnedId}`)
  }

  // Strategy 1: let the backend resolve the logged-in seller's own wallet.
  return apiRequest<SellerWallet>('/wallets/me')
}

function fetchWalletTransactionsApi(walletId: string): Promise<WalletTransaction[]> {
  return apiRequest<WalletTransaction[]>(
    `/wallets/${walletId}/transactions?limit=${TRANSACTIONS_PAGE_SIZE}&offset=0`,
  )
}

// ---------------------------------------------------------------------------
// React Query hooks
// ---------------------------------------------------------------------------

export function useSellerWalletQuery() {
  return useQuery({
    queryKey: ['seller-wallet'],
    queryFn: () => resolveSellerWalletApi(),
  })
}

export function useWalletTransactionsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['wallet-transactions', walletId],
    queryFn: () => fetchWalletTransactionsApi(walletId!),
    enabled: !!walletId,
  })
}
