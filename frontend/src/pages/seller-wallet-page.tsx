import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useBanksQuery, useMeQuery } from '@/shared/api'
import {
  MY_WALLET_QUERY_KEY,
  useMyWalletQuery,
  useUpdateBankDetailsMutation,
  useWalletTransactionsQuery,
  type SellerWallet,
  type WalletTransaction,
} from '@/shared/api/wallet'
import {
  PAYOUT_STATUS_META,
  usePayoutStatusQuery,
  useRunPayoutScreeningMutation,
  useSellerPayoutsQuery,
} from '@/shared/api/payout'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { AlertCircle, CalendarClock, Clock, Coins, Landmark, RefreshCw, ShieldCheck, TrendingUp, Wallet } from 'lucide-react'

const WALLET_ROLES = ['businessOwner']

const TX_TYPE_LABELS: Record<string, string> = {
  DEPOSIT: 'Tiền vào',
  WITHDRAWAL: 'Rút tiền',
  COMMISSION: 'Phí hoa hồng',
  ESCROW_HOLD: 'Tạm giữ ký quỹ',
  ESCROW_RELEASE: 'Giải phóng ký quỹ',
  ESCROW_REFUND: 'Hoàn ký quỹ',
  PAYOUT: 'Chi trả ngân hàng',
}

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function getErrorMessage(cause: unknown, fallback: string) {
  if (cause instanceof Error) return cause.message
  if (typeof cause === 'object' && cause !== null && 'error' in cause && typeof cause.error === 'string') return cause.error
  return fallback
}

function BalanceCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Wallet
  label: string
  value: string
  hint: string
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className="h-4 w-4 text-primary" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold tracking-tight">{value}</div>
        <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  )
}

function BankDetailsDialog({
  open,
  onOpenChange,
  wallet,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  wallet: SellerWallet
}) {
  const queryClient = useQueryClient()
  const updateBankDetails = useUpdateBankDetailsMutation()
  const { data: banks = [], isLoading: banksLoading } = useBanksQuery()
  const [bankId, setBankId] = useState(wallet.bankId ?? '')
  const [accountNumber, setAccountNumber] = useState(wallet.bankAccountNumber ?? '')
  const [accountHolder, setAccountHolder] = useState(wallet.bankAccountHolder ?? '')
  const [error, setError] = useState('')

  const save = async () => {
    if (!bankId || !accountNumber.trim() || !accountHolder.trim()) {
      setError('Vui lòng nhập đầy đủ thông tin ngân hàng.')
      return
    }
    try {
      await updateBankDetails.mutateAsync({
        bankId,
        bankAccountNumber: accountNumber.trim(),
        bankAccountHolder: accountHolder.trim(),
        currency: wallet.currency,
      })
      await queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY })
      await queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      setError('')
      onOpenChange(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể lưu thông tin ngân hàng.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thông tin ngân hàng</DialogTitle>
          <DialogDescription>
            Thông tin này được dùng để chi trả tự động. Lần chi trả đầu tiên sau mỗi thay đổi sẽ được chuyển cho quản trị viên kiểm tra.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="bank-name">Ngân hàng</Label>
            <Select value={bankId} onValueChange={(value) => setBankId(value ?? '')} disabled={banksLoading}>
              <SelectTrigger id="bank-name">
                <SelectValue placeholder={banksLoading ? 'Đang tải danh sách ngân hàng…' : 'Chọn ngân hàng'} />
              </SelectTrigger>
              <SelectContent>
                {banks.map((bank) => (
                  <SelectItem key={bank.id} value={bank.id!}>
                    {bank.name} ({bank.code})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="bank-account">Số tài khoản</Label>
            <Input id="bank-account" value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="bank-holder">Chủ tài khoản</Label>
            <Input id="bank-holder" value={accountHolder} onChange={(event) => setAccountHolder(event.target.value)} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Hủy</Button>
          <Button onClick={save} disabled={updateBankDetails.isPending}>
            {updateBankDetails.isPending ? 'Đang lưu…' : 'Lưu thông tin'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PayoutStatusWidget({ wallet }: { wallet: SellerWallet }) {
  const [bankOpen, setBankOpen] = useState(false)
  const [screeningMessage, setScreeningMessage] = useState('')
  const [screeningError, setScreeningError] = useState('')
  const queryClient = useQueryClient()
  const { data: payoutStatus, isLoading } = usePayoutStatusQuery(wallet.id)
  const runScreening = useRunPayoutScreeningMutation()
  const bankConfigured = Boolean(wallet.bankName && wallet.bankAccountNumber && wallet.bankAccountHolder)
  const minimumThreshold = Number(payoutStatus?.minimumThresholdMinor ?? 0)
  const availableBalance = Number(wallet.availableBalanceMinor ?? 0)
  const isEligible = Boolean(wallet.id) && bankConfigured && availableBalance >= minimumThreshold

  const handleRunScreening = async () => {
    setScreeningMessage('')
    setScreeningError('')
    try {
      const payout = await runScreening.mutateAsync(wallet.id!)
      const status = payout?.status ?? 'CREATED'
      const statusLabel = PAYOUT_STATUS_META[status]?.label ?? status
      setScreeningMessage(
        status === 'PAID'
          ? 'Chi trả đã qua kiểm tra và hoàn tất chuyển khoản mô phỏng.'
          : `Đã kiểm tra chi trả với trạng thái ${statusLabel}${payout?.flagReason ? `: ${payout.flagReason}` : '.'}`,
      )
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ['wallet-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['seller-payouts'] }),
        queryClient.invalidateQueries({ queryKey: ['payout-status'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-payouts'] }),
      ])
    } catch (cause) {
      setScreeningError(cause instanceof Error ? cause.message : 'Không thể kiểm tra điều kiện chi trả.')
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div>
            <CardTitle className="flex items-center gap-2">
              <CalendarClock className="h-5 w-5 text-primary" /> Trạng thái chi trả tự động
            </CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              Số dư đủ điều kiện được kiểm tra tự động; quản trị viên chỉ xử lý các trường hợp ngoại lệ.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setBankOpen(true)}>
              <Landmark className="mr-2 h-4 w-4" /> {bankConfigured ? 'Cập nhật ngân hàng' : 'Thêm ngân hàng'}
            </Button>
            <Button onClick={handleRunScreening} disabled={!isEligible || runScreening.isPending}>
              {runScreening.isPending ? <RefreshCw className="mr-2 h-4 w-4 animate-spin" /> : <ShieldCheck className="mr-2 h-4 w-4" />}
              Kiểm tra chi trả
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <p className="text-xs text-muted-foreground">Lần chi trả gần nhất</p>
              <p className="font-semibold">{isLoading ? 'Đang tải…' : formatDate(payoutStatus?.lastPayoutAt)}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Lần chạy kế tiếp</p>
              <p className="font-semibold">{isLoading ? 'Đang tải…' : formatDate(payoutStatus?.nextScheduledRunAt)}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Số dư tối thiểu</p>
              <p className="font-semibold">
                {formatMoneyMinor(minimumThreshold, wallet.currency ?? 'VND')}
              </p>
            </div>
          </div>
          {!bankConfigured && (
            <p className="text-sm text-muted-foreground">Hãy thêm thông tin ngân hàng trước khi kiểm tra chi trả.</p>
          )}
          {bankConfigured && availableBalance < minimumThreshold && (
            <p className="text-sm text-muted-foreground">Số dư khả dụng chưa đạt ngưỡng chi trả.</p>
          )}
          {screeningMessage && (
            <div className="rounded-md border border-success/30 bg-success/10 p-3 text-sm text-success">{screeningMessage}</div>
          )}
          {screeningError && (
            <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /> {screeningError}
            </div>
          )}
        </CardContent>
      </Card>
      <BankDetailsDialog open={bankOpen} onOpenChange={setBankOpen} wallet={wallet} />
    </>
  )
}

function PayoutHistory({ wallet }: { wallet: SellerWallet }) {
  const { data: payouts, isLoading } = useSellerPayoutsQuery(wallet.id)
  return (
    <Card>
      <CardHeader><CardTitle>Lịch sử chi trả</CardTitle></CardHeader>
      <CardContent>
        {isLoading ? <p className="text-sm text-muted-foreground">Đang tải…</p> : (
          <Table>
            <TableHeader><TableRow><TableHead>Ngày</TableHead><TableHead>Trạng thái</TableHead><TableHead className="text-right">Số tiền</TableHead></TableRow></TableHeader>
            <TableBody>
              {(payouts ?? []).map((payout) => {
                const meta = PAYOUT_STATUS_META[payout.status ?? ''] ?? { label: payout.status ?? 'Không xác định', variant: 'secondary' as const }
                return (
                  <TableRow key={payout.id}>
                    <TableCell>{formatDate(payout.paidAt ?? payout.createdAt)}</TableCell>
                    <TableCell><Badge variant={meta.variant}>{meta.label}</Badge></TableCell>
                    <TableCell className="text-right font-semibold">{formatMoneyMinor(Number(payout.amountMinor ?? 0), payout.currency ?? wallet.currency ?? 'VND')}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function TransactionHistory({ transactions, currency }: { transactions: WalletTransaction[]; currency: string }) {
  return (
    <Card>
      <CardHeader><CardTitle>Lịch sử giao dịch</CardTitle></CardHeader>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>Ngày</TableHead><TableHead>Loại</TableHead><TableHead>Diễn giải</TableHead><TableHead className="text-right">Số tiền</TableHead></TableRow></TableHeader>
          <TableBody>
            {transactions.map((transaction) => (
              <TableRow key={transaction.id}>
                <TableCell>{formatDate(transaction.createdAt)}</TableCell>
                <TableCell><Badge variant="secondary">{TX_TYPE_LABELS[transaction.transactionType ?? ''] ?? transaction.transactionType}</Badge></TableCell>
                <TableCell>{transaction.description ?? '—'}</TableCell>
                <TableCell className={`text-right font-semibold ${transaction.type === 'CREDIT' ? 'text-success' : 'text-destructive'}`}>
                  {transaction.type === 'CREDIT' ? '+' : '-'}{formatMoneyMinor(Number(transaction.amountMinor ?? 0), currency)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

export function SellerWalletPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const { data: wallet, isLoading, isError, error } = useMyWalletQuery()
  const { data: transactions } = useWalletTransactionsQuery(wallet?.id)

  if (isMeLoading) return <div className="py-8 text-center text-muted-foreground">Đang kiểm tra quyền truy cập…</div>
  if (!me?.role?.name || !WALLET_ROLES.includes(me.role.name)) {
    return <div className="py-8 text-center text-destructive">Chỉ chủ doanh nghiệp có thể truy cập ví người bán.</div>
  }
  if (isLoading) return <div className="py-8 text-center text-muted-foreground">Đang tải thông tin ví…</div>
  if (isError || !wallet) return <div className="py-8 text-center text-destructive">{getErrorMessage(error, 'Không thể tải thông tin ví.')}</div>

  const currency = wallet.currency ?? 'VND'
  const available = Number(wallet.availableBalanceMinor ?? 0)
  const pending = Number(wallet.pendingBalanceMinor ?? 0)
  const total = Number((wallet as SellerWallet & { totalBalanceMinor?: number }).totalBalanceMinor ?? available)

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Wallet className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:wallet', 'Ví tiền')}</h1>
          <p className="mt-1 text-muted-foreground">Tiền ký quỹ và số dư có thể chi trả được hiển thị riêng biệt.</p>
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <BalanceCard icon={TrendingUp} label="Số dư khả dụng" value={formatMoneyMinor(available, currency)} hint="Đủ điều kiện cho lần chi trả tự động kế tiếp" />
        <BalanceCard icon={Clock} label="Số dư đang chờ" value={formatMoneyMinor(pending, currency)} hint="Được giữ ký quỹ trong 7 ngày sau khi giao hàng" />
        <BalanceCard icon={Coins} label="Tổng số dư" value={formatMoneyMinor(total, currency)} hint="Doanh thu đã giải phóng từ các đơn hoàn tất sau khi trừ hoa hồng" />
      </div>
      <PayoutStatusWidget wallet={wallet} />
      <TransactionHistory transactions={transactions ?? []} currency={currency} />
      <PayoutHistory wallet={wallet} />
    </div>
  )
}
