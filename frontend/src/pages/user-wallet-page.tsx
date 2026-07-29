import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useMeQuery } from '@/shared/api'
import {
  MY_WALLET_QUERY_KEY,
  useMyWalletQuery,
  useWalletTransactionsQuery,
} from '@/shared/api/wallet'
import { topUpMyWallet, completeTopUp, getMyTopUps, simulateTransferred } from '@/shared/api/gen/sdk.gen'
import { useQuery, useMutation } from '@tanstack/react-query'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Clock, RefreshCw, TrendingUp, Wallet, ArrowDownToLine, CheckCircle2 } from 'lucide-react'

const TX_TYPE_LABELS: Record<string, string> = {
  TOP_UP: 'Nạp tiền',
  PAYMENT: 'Thanh toán',
  REFUND: 'Hoàn tiền',
  DEPOSIT: 'Nhận tiền',
  WITHDRAWAL: 'Rút tiền',
}

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—'
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

function TopUpDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [amountStr, setAmountStr] = useState('')
  const [error, setError] = useState('')
  const [pendingTopUp, setPendingTopUp] = useState<any>(null)

  const topUpMutation = useMutation({
    mutationFn: async (amountMinor: number) => {
      const res = await topUpMyWallet({
        body: { amountMinor, currency: 'VND' },
      })
      return res.data
    },
    onSuccess: (data) => {
      setPendingTopUp(data)
    },
    onError: (err: any) => {
      setError(err?.message || 'Có lỗi xảy ra')
    },
  })

  const completeMutation = useMutation({
    mutationFn: async (topUp: any) => {
      const res = await simulateTransferred({
        body: {
          paymentIntentId: topUp.id,
          gatewayTransactionId: 'MOCK_TOPUP_' + new Date().getTime(),
          status: 'SUCCESS',
          amountMinor: topUp.amountMinor,
          currency: 'VND',
        }
      })
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY })
      queryClient.invalidateQueries({ queryKey: ['wallet-transactions'] })
      setPendingTopUp(null)
      onOpenChange(false)
    },
  })

  const handleCreate = () => {
    setError('')
    const amount = Number.parseInt(amountStr.replace(/\D/g, ''), 10)
    if (isNaN(amount) || amount < 10000) {
      setError('Số tiền nạp tối thiểu là 10,000đ')
      return
    }
    topUpMutation.mutate(amount)
  }

  const handleSimulatePayment = () => {
    if (pendingTopUp) {
      completeMutation.mutate(pendingTopUp)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Nạp tiền vào ví</DialogTitle>
          <DialogDescription>
            Tạo mã VietQR để nạp tiền vào ví cá nhân của bạn.
          </DialogDescription>
        </DialogHeader>

        {!pendingTopUp ? (
          <div className="grid gap-4 py-4">
            {error && <div className="text-sm font-medium text-destructive">{error}</div>}
            <div className="grid gap-2">
              <Label htmlFor="amount">Số tiền nạp (VNĐ)</Label>
              <Input
                id="amount"
                type="number"
                placeholder="Ví dụ: 500000"
                value={amountStr}
                onChange={(e) => setAmountStr(e.target.value)}
              />
            </div>
          </div>
        ) : (
          <div className="grid gap-4 py-4 flex flex-col items-center">
            <p className="text-sm font-medium text-center">Quét mã QR sau để nạp tiền:</p>
            {pendingTopUp.paymentQrCodeBase64 && (
              <img
                src={pendingTopUp.paymentQrCodeBase64}
                alt="VietQR"
                className="w-48 h-48 mx-auto rounded border"
              />
            )}
            <p className="text-center font-bold text-lg mt-2">
              {formatMoneyMinor(pendingTopUp.amountMinor, 'VND')}
            </p>
            <Button
              className="w-full mt-4 bg-green-600 hover:bg-green-700 text-white"
              onClick={handleSimulatePayment}
              disabled={completeMutation.isPending}
            >
              {completeMutation.isPending ? 'Đang xử lý...' : 'Giả lập đã chuyển khoản xong'}
            </Button>
          </div>
        )}

        <DialogFooter>
          {!pendingTopUp && (
            <Button onClick={handleCreate} disabled={topUpMutation.isPending}>
              {topUpMutation.isPending ? 'Đang tạo...' : 'Tạo mã QR Nạp tiền'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function UserWalletPage() {
  const { data: user } = useMeQuery()
  const { data: wallet, isLoading: walletLoading } = useMyWalletQuery()
  const { data: transactions, isLoading: txLoading } = useWalletTransactionsQuery(wallet?.id)
  
  const [topUpOpen, setTopUpOpen] = useState(false)

  if (walletLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-muted-foreground">
        Đang tải thông tin ví...
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Ví cá nhân</h2>
          <p className="text-muted-foreground">
            Quản lý số dư, nạp tiền và lịch sử giao dịch thanh toán
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={() => setTopUpOpen(true)} className="gap-2">
            <ArrowDownToLine className="h-4 w-4" />
            Nạp tiền
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <BalanceCard
          icon={Wallet}
          label="Số dư khả dụng"
          value={wallet ? formatMoneyMinor(wallet.availableBalanceMinor ?? 0, wallet.currency || 'VND') : '—'}
          hint="Số tiền có thể dùng để thanh toán mua hàng ngay."
        />
        <BalanceCard
          icon={Clock}
          label="Số dư đang xử lý"
          value={wallet ? formatMoneyMinor(wallet.pendingBalanceMinor ?? 0, wallet.currency || 'VND') : '—'}
          hint="Các khoản tiền đang chờ cập nhật hệ thống."
        />
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-4">
          <CardTitle>Lịch sử giao dịch</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Ngày giao dịch</TableHead>
                  <TableHead>Mã giao dịch</TableHead>
                  <TableHead>Loại</TableHead>
                  <TableHead>Diễn giải</TableHead>
                  <TableHead className="text-right">Số tiền</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {txLoading ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                      Đang tải dữ liệu...
                    </TableCell>
                  </TableRow>
                ) : !transactions || transactions.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                      Chưa có giao dịch nào
                    </TableCell>
                  </TableRow>
                ) : (
                  transactions.map((tx: any) => (
                    <TableRow key={tx.id}>
                      <TableCell className="whitespace-nowrap">
                        {formatDate(tx.createdAt)}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {tx.id.substring(0, 8)}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {TX_TYPE_LABELS[tx.transactionType] || tx.transactionType}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate" title={tx.description}>
                        {tx.description}
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        <span className={tx.type === 'CREDIT' ? 'text-success' : 'text-destructive'}>
                          {tx.type === 'CREDIT' ? '+' : '-'}
                          {formatMoneyMinor(tx.amountMinor, wallet?.currency || 'VND')}
                        </span>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
      
      {topUpOpen && (
        <TopUpDialog open={topUpOpen} onOpenChange={setTopUpOpen} />
      )}
    </div>
  )
}
