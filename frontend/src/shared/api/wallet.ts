import { useQuery } from '@tanstack/react-query'

/**
 * Seller wallet data layer.
 *
 * Day 2 (Dev 5) ships against a mock so the dashboard UI can be built without
 * blocking on the backend. The real endpoints already exist
 * (`WalletResource`: `GET /wallets`, `GET /wallets/{id}/transactions`) — flip
 * `VITE_WALLET_API=real` to read them once the generated SDK and the
 * seller→business→wallet linkage land (planned for Week 2, Day 3).
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

async function apiRequest<T>(path: string): Promise<T> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  const token = localStorage.getItem('access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE}/api${path}`, { headers })
  if (!response.ok) {
    throw new Error((await response.text()) || `Request failed with ${response.status}`)
  }
  return response.json() as Promise<T>
}

async function fetchSellerWalletApi(): Promise<SellerWallet | null> {
  // Placeholder resolution until the seller→business→wallet linkage (V17) is
  // wired: return the first SELLER-owned wallet in the caller's tenant.
  const wallets = await apiRequest<SellerWallet[]>('/wallets')
  return wallets.find((w) => w.ownerType === 'SELLER') ?? wallets[0] ?? null
}

function fetchWalletTransactionsApi(walletId: string): Promise<WalletTransaction[]> {
  return apiRequest<WalletTransaction[]>(`/wallets/${walletId}/transactions`)
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
      walletDataSource === 'api' ? fetchSellerWalletApi() : fetchSellerWalletMock(),
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
