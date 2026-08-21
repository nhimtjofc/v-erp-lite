import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { CalendarDays, Check, CircleDollarSign, Clock3, CreditCard, FastForward, Users, X } from 'lucide-react'
import {
  ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY,
  SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY,
  subscriptionQueryKey,
  useAdminSubscriptionPaymentsQuery,
  useApproveSubscriptionPaymentMutation,
  useMeQuery,
  useRejectSubscriptionPaymentMutation,
  useSubscriptionFinancialStatsQuery,
  type SubscriptionPayment,
} from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { cn } from '@/shared/lib/cn'

const statusLabels: Record<SubscriptionPayment['status'], string> = {
  PENDING: 'Chờ thanh toán',
  SUBMITTED: 'Chờ xác nhận',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  CANCELLED: 'Đã hủy',
  FAILED: 'Thất bại',
}

export function AdminSubscriptionPaymentsPage() {
  const queryClient = useQueryClient()
  const { data: me, isLoading: meLoading } = useMeQuery()
  const { data: payments = [], isLoading } = useAdminSubscriptionPaymentsQuery()
  const { data: stats, isLoading: statsLoading } = useSubscriptionFinancialStatsQuery()
  const approve = useApproveSubscriptionPaymentMutation()
  const reject = useRejectSubscriptionPaymentMutation()
  const [rejecting, setRejecting] = useState<SubscriptionPayment | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const submitted = payments.filter((payment) => payment.status === 'SUBMITTED')

  const refresh = async (payment: SubscriptionPayment) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: subscriptionQueryKey(payment.tenantId) }),
    ])
  }

  const approvePayment = async (payment: SubscriptionPayment) => {
    try {
      setError('')
      await approve.mutateAsync(payment.id)
      await refresh(payment)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể duyệt thanh toán.')
    }
  }

  const rejectPayment = async () => {
    if (!rejecting || !reason.trim()) return
    try {
      setError('')
      await reject.mutateAsync({ id: rejecting.id, rejectionReason: reason.trim() })
      await refresh(rejecting)
      setRejecting(null)
      setReason('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể từ chối thanh toán.')
    }
  }

  if (meLoading) return <p className="py-8 text-center text-muted-foreground">Đang kiểm tra quyền truy cập...</p>
  if (me?.role?.name !== 'admin') return <p className="py-8 text-center text-destructive">Chỉ quản trị viên hệ thống có thể duyệt thanh toán subscription.</p>

  return <div className="space-y-6">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="flex gap-3">
        <CreditCard className="mt-1 h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tài chính subscription</h1>
          <p className="mt-1 text-muted-foreground">Theo dõi lợi nhuận ghi nhận và duyệt thanh toán của các tenant.</p>
        </div>
      </div>
      <Link to="/admin/tenants" className={cn(buttonVariants({ variant: 'outline' }), 'gap-2')}>
        <FastForward className="h-4 w-4" />Mở công cụ showcase
      </Link>
    </div>

    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Card><CardContent className="pt-5">
        <div className="flex items-center justify-between"><p className="text-sm text-muted-foreground">Lợi nhuận ghi nhận</p><CircleDollarSign className="h-5 w-5 text-success" /></div>
        <p className="mt-2 text-2xl font-bold">{statsLoading ? '—' : formatMoneyMinor(stats?.recognizedProfitMinor ?? 0, stats?.currency)}</p>
        <p className="mt-1 text-xs text-muted-foreground">{stats?.approvedPaymentCount ?? 0} thanh toán đã duyệt</p>
      </CardContent></Card>
      <Card><CardContent className="pt-5">
        <div className="flex items-center justify-between"><p className="text-sm text-muted-foreground">Lợi nhuận tháng này</p><CalendarDays className="h-5 w-5 text-primary" /></div>
        <p className="mt-2 text-2xl font-bold">{statsLoading ? '—' : formatMoneyMinor(stats?.currentMonthProfitMinor ?? 0, stats?.currency)}</p>
        <p className="mt-1 text-xs text-muted-foreground">Theo thời điểm admin duyệt</p>
      </CardContent></Card>
      <Card><CardContent className="pt-5">
        <div className="flex items-center justify-between"><p className="text-sm text-muted-foreground">Chờ xác nhận</p><Clock3 className="h-5 w-5 text-amber-500" /></div>
        <p className="mt-2 text-2xl font-bold">{statsLoading ? '—' : formatMoneyMinor(stats?.pendingRevenueMinor ?? 0, stats?.currency)}</p>
        <p className="mt-1 text-xs text-muted-foreground">{submitted.length} yêu cầu cần duyệt</p>
      </CardContent></Card>
      <Card><CardContent className="pt-5">
        <div className="flex items-center justify-between"><p className="text-sm text-muted-foreground">Thuê bao hoạt động</p><Users className="h-5 w-5 text-primary" /></div>
        <p className="mt-2 text-2xl font-bold">{statsLoading ? '—' : formatMoneyMinor(stats?.activeSubscriptionValueMinor ?? 0, stats?.currency)}</p>
        <p className="mt-1 text-xs text-muted-foreground">{stats?.activeSubscriptionCount ?? 0} thuê bao · Base: {stats?.baseSubscriptionCount ?? 0} · Premium: {stats?.premiumSubscriptionCount ?? 0}</p>
      </CardContent></Card>
    </div>

    <p className="rounded-md border border-dashed bg-muted/30 px-4 py-3 text-xs text-muted-foreground">
      Lợi nhuận ghi nhận gồm toàn bộ thanh toán đã duyệt và giá trị mở sổ của subscription được seed mặc định. Subscription hết hạn không làm giảm lợi nhuận đã ghi nhận. Hệ thống chưa có sổ chi phí vận hành nên số liệu này chưa trừ chi phí.
    </p>

    {error && <p className="text-sm text-destructive">{error}</p>}

    <Card>
      <CardHeader><CardTitle>Danh sách yêu cầu thanh toán</CardTitle></CardHeader>
      <CardContent>
        {isLoading ? <p className="text-muted-foreground">Đang tải...</p> : <Table>
          <TableHeader><TableRow><TableHead>Gửi lúc</TableHead><TableHead>Tenant</TableHead><TableHead>Gói</TableHead><TableHead>Mã giao dịch</TableHead><TableHead>Trạng thái</TableHead><TableHead className="text-right">Số tiền</TableHead><TableHead /></TableRow></TableHeader>
          <TableBody>{payments.map((payment) => <TableRow key={payment.id}>
            <TableCell>{payment.submittedAt ? new Date(payment.submittedAt).toLocaleString('vi-VN') : '—'}</TableCell>
            <TableCell><code className="text-xs">{payment.tenantId}</code></TableCell>
            <TableCell>{payment.planCode} · {payment.billingPeriod === 'ANNUAL' ? 'Năm' : 'Tháng'}</TableCell>
            <TableCell>{payment.paymentReference ?? '—'}</TableCell>
            <TableCell><Badge variant={payment.status === 'APPROVED' ? 'default' : payment.status === 'REJECTED' ? 'destructive' : 'secondary'}>{statusLabels[payment.status]}</Badge>{payment.rejectionReason && <p className="mt-1 text-xs text-destructive">{payment.rejectionReason}</p>}</TableCell>
            <TableCell className="text-right font-semibold">{formatMoneyMinor(payment.amountMinor, payment.currency)}</TableCell>
            <TableCell>{payment.status === 'SUBMITTED' && <div className="flex justify-end gap-2"><Button size="sm" onClick={() => approvePayment(payment)} disabled={approve.isPending}><Check className="mr-1 h-4 w-4" />Duyệt</Button><Button size="sm" variant="destructive" onClick={() => setRejecting(payment)}><X className="mr-1 h-4 w-4" />Từ chối</Button></div>}</TableCell>
          </TableRow>)}</TableBody>
        </Table>}
      </CardContent>
    </Card>

    {rejecting && <Dialog open onOpenChange={(open) => !open && setRejecting(null)}>
      <DialogContent>
        <DialogHeader><DialogTitle>Từ chối thanh toán</DialogTitle></DialogHeader>
        <div className="space-y-2"><Label htmlFor="reason">Lý do từ chối</Label><Input id="reason" value={reason} onChange={(event) => setReason(event.target.value)} /></div>
        <DialogFooter><Button variant="outline" onClick={() => setRejecting(null)}>Hủy</Button><Button variant="destructive" disabled={!reason.trim() || reject.isPending} onClick={rejectPayment}>Từ chối</Button></DialogFooter>
      </DialogContent>
    </Dialog>}
  </div>
}
