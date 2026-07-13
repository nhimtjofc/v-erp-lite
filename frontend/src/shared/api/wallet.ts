import { useQuery } from '@tanstack/react-query'

/**
 * Seller wallet data layer.
 *
 * `VITE_WALLET_API=real` reads the live backend (`WalletResource`:
 * `GET /wallets`, `GET /wallets/{id}/transactions`); otherwise a mock backs the
 * UI so it works without the stack running.
 *
 * The one piece not yet wired end-to-end is mapping the logged-in seller to
 * their wallet (needs a User→Business link from Dev 1/2). That resolution is
 * isolated in `resolveSellerWalletApi` below — the single seam to update when
 * the linkage lands; nothing else in the dashboard changes.
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

export const walletDataSource: 'mock' | 'api' =
  import.meta.env.VITE_WALLET_API === 'real' ? 'api' : 'mock'

// ---------------------------------------------------------------------------
// Real API
// ---------------------------------------------------------------------------

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
 * The proper User→Business link isn't wired yet, so we fall back to a
 * best-effort lookup. When the backend ships one of the options below, enable
 * it here; the hooks, page and mock all stay unchanged.
 *
 *   1. GET /wallets/me                               ← preferred, once Dev 2 adds it
 *   2. /me exposes businessId → filter by ownerId    ← once User→Business lands
 *   3. VITE_SELLER_WALLET_ID pins a wallet           ← demo / single-seller testing
 *   4. Fallback: first SELLER-owned wallet in tenant ← correct only for 1 seller/tenant
 */
async function resolveSellerWalletApi(): Promise<SellerWallet | null> {
  // Strategy 3: explicit override, handy for demoing a specific seller's wallet
  // while the real resolution is pending.
  const pinnedId = import.meta.env.VITE_SELLER_WALLET_ID as string | undefined
  if (pinnedId) {
    return apiRequest<SellerWallet>(`/wallets/${pinnedId}`)
  }

  // Strategy 1 — enable when the endpoint exists:
  //   return apiRequest<SellerWallet>('/wallets/me')
  // Strategy 2 — enable when /users/me returns a businessId:
  //   const me = await apiRequest<{ businessId?: string }>('/users/me')
  //   if (me.businessId) {
  //     const owned = await apiRequest<SellerWallet[]>(
  //       `/wallets?ownerType=SELLER&ownerId=${me.businessId}`)
  //     return owned[0] ?? null
  //   }

  // Strategy 4 (current): first SELLER-owned wallet in the caller's tenant.
  const wallets = await apiRequest<SellerWallet[]>('/wallets?ownerType=SELLER')
  return wallets[0] ?? null
}

function fetchWalletTransactionsApi(walletId: string): Promise<WalletTransaction[]> {
  return apiRequest<WalletTransaction[]>(
    `/wallets/${walletId}/transactions?limit=${TRANSACTIONS_PAGE_SIZE}&offset=0`,
  )
}

// ---------------------------------------------------------------------------
// Mock
// ---------------------------------------------------------------------------

const MOCK_WALLET: SellerWallet = {
  id: 'mock-wallet-0001',
  tenantId: 'v-erp-app',
  ownerType: 'SELLER',
  ownerId: 'mock-business-0001',
  availableBalanceMinor: 4_250_000, // VND — exponent 0
  pendingBalanceMinor: 1_800_000,
  currency: 'VND',
  createdAt: '2026-06-01T08:00:00Z',
  updatedAt: '2026-07-07T10:30:00Z',
}

const MOCK_TRANSACTIONS: WalletTransaction[] = [
  {
    id: 'mock-tx-0007', walletId: MOCK_WALLET.id, amountMinor: 950_000,
    type: 'CREDIT', transactionType: 'ESCROW_HOLD', referenceType: 'ORDER', referenceId: 'mock-order-0007',
    sequenceNo: 7, description: 'Tạm giữ tiền đơn #10007', createdAt: '2026-07-07T10:30:00Z',
  },
  {
    id: 'mock-tx-0006', walletId: MOCK_WALLET.id, amountMinor: 1_200_000,
    type: 'CREDIT', transactionType: 'ESCROW_RELEASE', referenceType: 'ORDER', referenceId: 'mock-order-0005',
    sequenceNo: 6, description: 'Giải ngân đơn #10005', createdAt: '2026-07-05T14:12:00Z',
  },
  {
    id: 'mock-tx-0005', walletId: MOCK_WALLET.id, amountMinor: 2_000_000,
    type: 'DEBIT', transactionType: 'PAYOUT', referenceType: 'PAYOUT', referenceId: 'mock-payout-0002',
    sequenceNo: 5, description: 'Rút tiền về ngân hàng', createdAt: '2026-07-03T09:00:00Z',
  },
  {
    id: 'mock-tx-0004', walletId: MOCK_WALLET.id, amountMinor: 63_000,
    type: 'DEBIT', transactionType: 'COMMISSION', referenceType: 'ORDER', referenceId: 'mock-order-0004',
    sequenceNo: 4, description: 'Hoa hồng nền tảng 5% đơn #10004', createdAt: '2026-07-02T16:45:00Z',
  },
  {
    id: 'mock-tx-0003', walletId: MOCK_WALLET.id, amountMinor: 1_197_000,
    type: 'CREDIT', transactionType: 'ESCROW_RELEASE', referenceType: 'ORDER', referenceId: 'mock-order-0004',
    sequenceNo: 3, description: 'Giải ngân đơn #10004', createdAt: '2026-07-02T16:45:00Z',
  },
  {
    id: 'mock-tx-0002', walletId: MOCK_WALLET.id, amountMinor: 350_000,
    type: 'DEBIT', transactionType: 'ESCROW_REFUND', referenceType: 'ORDER', referenceId: 'mock-order-0003',
    sequenceNo: 2, description: 'Hoàn tiền đơn hủy #10003', createdAt: '2026-06-28T11:20:00Z',
  },
  {
    id: 'mock-tx-0001', walletId: MOCK_WALLET.id, amountMinor: 500_000,
    type: 'CREDIT', transactionType: 'DEPOSIT', referenceType: null, referenceId: null,
    sequenceNo: 1, description: 'Nạp tiền khởi tạo ví', createdAt: '2026-06-01T08:05:00Z',
  },
]

const mockDelay = () => new Promise((resolve) => setTimeout(resolve, 350))

async function fetchSellerWalletMock(): Promise<SellerWallet> {
  await mockDelay()
  return MOCK_WALLET
}

async function fetchWalletTransactionsMock(): Promise<WalletTransaction[]> {
  await mockDelay()
  return MOCK_TRANSACTIONS
}

// ---------------------------------------------------------------------------
// React Query hooks (same shape regardless of data source)
// ---------------------------------------------------------------------------

export function useSellerWalletQuery() {
  return useQuery({
    queryKey: ['seller-wallet', walletDataSource],
    queryFn: () =>
      walletDataSource === 'api' ? resolveSellerWalletApi() : fetchSellerWalletMock(),
  })
}

export function useWalletTransactionsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['wallet-transactions', walletDataSource, walletId],
    queryFn: () =>
      walletDataSource === 'api'
        ? fetchWalletTransactionsApi(walletId!)
        : fetchWalletTransactionsMock(),
    enabled: !!walletId,
  })
}
