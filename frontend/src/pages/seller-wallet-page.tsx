import { useTranslation } from 'react-i18next'
import { useMeQuery } from '@/shared/api'
import {
  useSellerWalletQuery,
  useWalletTransactionsQuery,
  walletDataSource,
  type WalletTransaction,
} from '@/shared/api/wallet'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Wallet, TrendingUp, Clock, Coins } from 'lucide-react'

/** Roles allowed to manage the seller wallet (matches WalletResource on the backend). */
const WALLET_ROLES = ['businessOwner', 'operator']

/** Vietnamese labels for ledger business-event types. */
const TX_TYPE_LABELS: Record<string, string> = {
  DEPOSIT: 'Nạp tiền',
  WITHDRAWAL: 'Rút tiền',
  COMMISSION: 'Hoa hồng',
  ESCROW_HOLD: 'Tạm giữ',
  ESCROW_RELEASE: 'Giải ngân',
  ESCROW_REFUND: 'Hoàn tiền',
  PAYOUT: 'Rút về ngân hàng',
}

function formatDate(value?: string): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function TransactionAmount({ tx, currency }: { tx: WalletTransaction; currency: string }) {
  const isCredit = tx.type === 'CREDIT'
  const sign = isCredit ? '+' : '-'
  return (
    <span className={isCredit ? 'font-semibold text-emerald-600' : 'font-semibold text-destructive'}>
      {sign}
      {formatMoneyMinor(tx.amountMinor, currency)}
    </span>
  )
}

function BalanceCard({
  icon: Icon,
  label,
  value,
  hint,
  isLoading,
  accent,
}: {
  icon: typeof Wallet
  label: string
  value: string
  hint?: string
  isLoading: boolean
  accent?: boolean
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className={`h-4 w-4 ${accent ? 'text-primary' : 'text-muted-foreground'}`} />
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="h-8 w-32 animate-pulse rounded bg-muted" />
        ) : (
          <div className="text-2xl font-bold tracking-tight">{value}</div>
        )}
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

/**
 * Seller wallet dashboard: available balance, pending (escrow) balance and
 * transaction history. Reads from the wallet data layer (mock by default,
 * real backend when `VITE_WALLET_API=real`).
 */
export function SellerWalletPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name

  const {
    data: wallet,
    isLoading: isWalletLoading,
    isError: isWalletError,
    error: walletError,
  } = useSellerWalletQuery()

  const {
    data: transactions,
    isLoading: isTxLoading,
  } = useWalletTransactionsQuery(wallet?.id)

  if (isMeLoading) {
    return <div className="py-8 text-center text-muted-foreground">Loading access control...</div>
  }

  if (!roleName || !WALLET_ROLES.includes(roleName)) {
    return (
      <div className="flex min-h-[300px] flex-col items-center justify-center space-y-4 rounded-lg border border-dashed p-8 text-center">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="max-w-md text-muted-foreground">Only sellers can access the wallet.</p>
      </div>
    )
  }

  const currency = wallet?.currency ?? 'VND'
  const txList = transactions ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Wallet className="h-7 w-7 text-primary" />
          <div>
            <h1 className="text-3xl font-bold tracking-tight">{t('nav:wallet', 'Wallet')}</h1>
            <p className="mt-1 text-muted-foreground">
              Available balance, pending balance and transaction history.
            </p>
          </div>
        </div>
        <Badge variant={walletDataSource === 'api' ? 'default' : 'secondary'}>
          {walletDataSource === 'api' ? 'Live API' : 'Mock data'}
        </Badge>
      </div>

      {isWalletError ? (
        <Card>
          <CardContent className="py-8 text-center">
            <p className="font-semibold text-destructive">Không tải được ví.</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {(walletError as Error)?.message || 'Vui lòng thử lại sau.'}
            </p>
          </CardContent>
        </Card>
      ) : !isWalletLoading && !wallet ? (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">
            Chưa có ví. Ví sẽ được khởi tạo tự động khi có giao dịch đầu tiên.
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <BalanceCard
              icon={TrendingUp}
              label="Số dư khả dụng"
              value={formatMoneyMinor(wallet?.availableBalanceMinor ?? 0, currency)}
              hint="Available balance — có thể rút"
              isLoading={isWalletLoading}
              accent
            />
            <BalanceCard
              icon={Clock}
              label="Số dư tạm giữ"
              value={formatMoneyMinor(wallet?.pendingBalanceMinor ?? 0, currency)}
              hint="Pending / escrow — chờ giải ngân"
              isLoading={isWalletLoading}
            />
            <BalanceCard
              icon={Coins}
              label="Tổng số dư"
              value={formatMoneyMinor(
                (wallet?.availableBalanceMinor ?? 0) + (wallet?.pendingBalanceMinor ?? 0),
                currency,
              )}
              isLoading={isWalletLoading}
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Lịch sử giao dịch</CardTitle>
            </CardHeader>
            <CardContent>
              {isTxLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  {t('common:loading', 'Loading...')}
                </p>
              ) : txList.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">Chưa có giao dịch nào.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Thời gian</TableHead>
                        <TableHead>Loại</TableHead>
                        <TableHead>Mô tả</TableHead>
                        <TableHead className="text-right">Số tiền</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {txList.map((tx) => (
                        <TableRow key={tx.id}>
                          <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                            {formatDate(tx.createdAt)}
                          </TableCell>
                          <TableCell>
                            <Badge variant="secondary">
                              {TX_TYPE_LABELS[tx.transactionType] ?? tx.transactionType}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-xs truncate">{tx.description ?? '—'}</TableCell>
                          <TableCell className="whitespace-nowrap text-right">
                            <TransactionAmount tx={tx} currency={currency} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
