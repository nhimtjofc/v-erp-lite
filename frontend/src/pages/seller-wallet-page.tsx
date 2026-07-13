import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useMeQuery } from '@/shared/api'
import {
  useSellerWalletQuery,
  useWalletTransactionsQuery,
  type SellerWallet,
  type WalletTransaction,
} from '@/shared/api/wallet'
import {
  useCreatePayoutMutation,
  useSellerPayoutsQuery,
  PAYOUT_STATUS_META,
  type PayoutRequest,
} from '@/shared/api/payout'
import { formatMoneyMinor, parseMoneyToMinor } from '@/shared/utils/currency'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/shared/ui/dialog'
import { Wallet, TrendingUp, Clock, Coins, ArrowUpRight } from 'lucide-react'

/** Roles allowed to manage the seller wallet (matches WalletResource on the backend). */
const WALLET_ROLES = ['businessOwner']

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
 * Payout request form (dialog). Lets a seller submit a withdrawal from their
 * available balance to a bank account. Validates the amount against the
 * available balance before enabling submit, so a request can never exceed what
 * the wallet can pay out.
 */
function PayoutRequestDialog({
  open,
  onOpenChange,
  wallet,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  wallet: SellerWallet
}) {
  const queryClient = useQueryClient()
  const createPayout = useCreatePayoutMutation()
  const currency = wallet.currency

  const [amount, setAmount] = useState('')
  const [bankName, setBankName] = useState('')
  const [bankAccountNumber, setBankAccountNumber] = useState('')
  const [bankAccountHolder, setBankAccountHolder] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reset = () => {
    setAmount('')
    setBankName('')
    setBankAccountNumber('')
    setBankAccountHolder('')
    setError(null)
    createPayout.reset()
  }

  const handleOpenChange = (next: boolean) => {
    if (!next) reset()
    onOpenChange(next)
  }

  const amountMinor = parseMoneyToMinor(amount, currency)
  const available = wallet.availableBalanceMinor
  const exceedsBalance = amountMinor !== null && amountMinor > available

  const canSubmit =
    amountMinor !== null &&
    !exceedsBalance &&
    bankName.trim() !== '' &&
    bankAccountNumber.trim() !== '' &&
    bankAccountHolder.trim() !== '' &&
    !createPayout.isPending

  const handleSubmit = async () => {
    setError(null)
    if (amountMinor === null) {
      setError('Vui lòng nhập số tiền hợp lệ lớn hơn 0.')
      return
    }
    if (exceedsBalance) {
      setError('Số tiền rút vượt quá số dư khả dụng.')
      return
    }
    try {
      await createPayout.mutateAsync({
        walletId: wallet.id,
        amountMinor,
        currency,
        bankName: bankName.trim(),
        bankAccountNumber: bankAccountNumber.trim(),
        bankAccountHolder: bankAccountHolder.trim(),
      })
      // Refresh wallet balance, ledger and payout list so the new PENDING
      // request (and any reserved balance) shows immediately.
      queryClient.invalidateQueries({ queryKey: ['seller-wallet'] })
      queryClient.invalidateQueries({ queryKey: ['wallet-transactions'] })
      queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      handleOpenChange(false)
    } catch (err) {
      setError((err as Error)?.message || 'Gửi yêu cầu rút tiền thất bại. Vui lòng thử lại.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Yêu cầu rút tiền</DialogTitle>
          <DialogDescription>
            Số dư khả dụng: {formatMoneyMinor(available, currency)}. Yêu cầu sẽ chờ Admin phê duyệt.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="payout-amount">Số tiền rút ({currency})</Label>
            <Input
              id="payout-amount"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Ví dụ: 1000000"
            />
            {exceedsBalance && (
              <p className="text-xs text-destructive">Vượt quá số dư khả dụng.</p>
            )}
            {amountMinor !== null && !exceedsBalance && (
              <p className="text-xs text-muted-foreground">
                Rút {formatMoneyMinor(amountMinor, currency)}
              </p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="payout-bank">Ngân hàng</Label>
            <Input
              id="payout-bank"
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              placeholder="Ví dụ: Vietcombank"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="payout-account-number">Số tài khoản</Label>
            <Input
              id="payout-account-number"
              value={bankAccountNumber}
              onChange={(e) => setBankAccountNumber(e.target.value)}
              placeholder="Số tài khoản ngân hàng"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="payout-account-holder">Chủ tài khoản</Label>
            <Input
              id="payout-account-holder"
              value={bankAccountHolder}
              onChange={(e) => setBankAccountHolder(e.target.value)}
              placeholder="Tên chủ tài khoản"
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Hủy
            </Button>
            <Button onClick={handleSubmit} disabled={!canSubmit}>
              {createPayout.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** List of the seller's own payout requests and their approval status. */
function PayoutHistoryCard({ walletId, currency }: { walletId: string; currency: string }) {
  const { data: payouts, isLoading } = useSellerPayoutsQuery(walletId)
  const list = payouts ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>Yêu cầu rút tiền</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="py-8 text-center text-sm text-muted-foreground">Đang tải...</p>
        ) : list.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            Chưa có yêu cầu rút tiền nào.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Thời gian</TableHead>
                  <TableHead>Ngân hàng</TableHead>
                  <TableHead>Số tài khoản</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Số tiền</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.map((payout: PayoutRequest) => {
                  const status = PAYOUT_STATUS_META[payout.status] ?? {
                    label: payout.status,
                    variant: 'secondary' as const,
                  }
                  return (
                    <TableRow key={payout.id}>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {formatDate(payout.createdAt)}
                      </TableCell>
                      <TableCell>{payout.bankName}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {payout.bankAccountNumber}
                      </TableCell>
                      <TableCell>
                        <Badge variant={status.variant}>{status.label}</Badge>
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-right font-semibold">
                        {formatMoneyMinor(payout.amountMinor, currency)}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * Seller wallet dashboard: available balance, pending (escrow) balance and
 * transaction history. Reads from the live backend via the wallet data layer.
 */
export function SellerWalletPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const [payoutOpen, setPayoutOpen] = useState(false)

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
        <div className="flex items-center gap-3">
          {wallet && (
            <Button onClick={() => setPayoutOpen(true)}>
              <ArrowUpRight className="mr-2 h-4 w-4" />
              Rút tiền
            </Button>
          )}
        </div>
      </div>

      {wallet && (
        <PayoutRequestDialog open={payoutOpen} onOpenChange={setPayoutOpen} wallet={wallet} />
      )}

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

          {wallet && <PayoutHistoryCard walletId={wallet.id} currency={currency} />}
        </>
      )}
    </div>
  )
}
