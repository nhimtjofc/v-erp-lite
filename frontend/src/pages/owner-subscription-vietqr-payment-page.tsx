import { Link, useParams } from '@tanstack/react-router'
import { CheckCircle2, ChevronLeft, Clock3, CreditCard, Landmark, LockKeyhole, Package, ShieldCheck } from 'lucide-react'
import { useMySubscriptionPaymentsQuery } from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'

const T = {
  back: 'Quay lại Gói & thanh toán',
  loading: 'Đang tải phiên thanh toán...',
  missing: 'Không tìm thấy yêu cầu thanh toán.',
  secureGateway: 'Cổng thanh toán Sandbox bảo mật cao',
  title: 'Hoàn tất thanh toán',
  orderReady: 'Yêu cầu thanh toán gói của bạn đã sẵn sàng.',
  chooseMethod: 'Chọn phương thức thanh toán',
  methodHint: 'Cổng thanh toán giả lập hỗ trợ kiểm tra luồng bảo mật HMAC & Replay Protection.',
  vietQr: 'Chuyển khoản VietQR Sandbox',
  vietQrHint: 'Xác thực giao dịch tự động qua webhook ký mã HMAC.',
  vnpay: 'VNPay',
  card: 'Thẻ quốc tế (Visa/Mastercard)',
  comingSoon: 'Sắp ra mắt',
  backToShopping: 'Quay lại chọn gói',
  continue: 'Tiếp tục thanh toán',
  summary: 'Tóm tắt đơn hàng',
  subscription: 'Gói dịch vụ',
  oneService: '1 gói dịch vụ',
  orderStatus: 'Trạng thái đơn',
  pending: 'PENDING_PAYMENT',
  total: 'Tổng thanh toán',
  verified: 'Hệ thống xác thực chế độ dư & chữ ký số',
  expires: 'Phiên giao dịch được khóa bảo vệ trong 30 phút',
  idempotency: 'Idempotency ngăn chặn nạp tiền trùng lặp tuyệt đối',
}

export function OwnerSubscriptionVietQrPaymentPage() {
  const { id } = useParams({ from: '/_authenticated/subscription/checkout/$id' })
  const { data: payments = [], isLoading } = useMySubscriptionPaymentsQuery()
  const payment = payments.find((item) => item.id === id)
  const paymentCode = `SUB-${id.slice(0, 8).toUpperCase()}`

  if (isLoading) return <p className="py-8 text-center text-muted-foreground">{T.loading}</p>
  if (!payment || payment.status !== 'PENDING') return <p className="py-8 text-center text-destructive">{T.missing}</p>

  return (
    <main className="mx-auto max-w-7xl px-1 pb-12 pt-2 sm:px-4">
      <div className="mb-6 max-w-2xl">
        <Link to="/subscription" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground">
          <ChevronLeft className="h-4 w-4" />
          {T.back}
        </Link>
        <p className="mb-2 inline-flex items-center gap-1.5 rounded-md bg-emerald-500/10 px-2 py-1 text-xs font-semibold text-emerald-700 dark:text-emerald-400">
          <ShieldCheck className="h-3.5 w-3.5" />
          {T.secureGateway}
        </p>
        <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">{T.title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{T.orderReady} <span className="font-semibold text-foreground">#{paymentCode}</span></p>
      </div>

      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(19rem,0.85fr)]">
        <Card className="border-border/80 shadow-sm">
          <CardContent className="p-5 sm:p-6">
            <div className="mb-5">
              <h2 className="text-base font-bold">{T.chooseMethod}</h2>
              <p className="mt-1 text-sm text-muted-foreground">{T.methodHint}</p>
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-3 rounded-xl border-2 border-primary/70 bg-primary/5 p-3 shadow-sm">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                  <Landmark className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{T.vietQr}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{T.vietQrHint}</p>
                </div>
                <CheckCircle2 className="h-5 w-5 shrink-0 fill-primary text-primary-foreground" aria-label="Đã chọn VietQR" />
              </div>

              <div className="flex items-center gap-3 rounded-xl border border-dashed border-border bg-muted/20 p-3 text-muted-foreground/70" aria-disabled="true">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-muted"><Landmark className="h-5 w-5" /></span>
                <p className="flex-1 text-sm font-medium">{T.vnpay}</p>
                <Badge variant="secondary" className="text-[10px] font-medium">{T.comingSoon}</Badge>
              </div>

              <div className="flex items-center gap-3 rounded-xl border border-dashed border-border bg-muted/20 p-3 text-muted-foreground/70" aria-disabled="true">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-muted"><CreditCard className="h-5 w-5" /></span>
                <p className="flex-1 text-sm font-medium">{T.card}</p>
                <Badge variant="secondary" className="text-[10px] font-medium">{T.comingSoon}</Badge>
              </div>
            </div>

            <div className="mt-5 flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm font-semibold text-muted-foreground">{T.backToShopping}</p>
              <Link to="/subscription/checkout/$id/webhook" params={{ id }}>
                <Button className="w-full px-5 sm:w-auto"><LockKeyhole className="h-4 w-4" />{T.continue}</Button>
              </Link>
            </div>
          </CardContent>
        </Card>

        <aside className="space-y-5">
          <Card className="border-border/80 shadow-sm">
            <CardContent className="p-5">
              <h2 className="mb-4 text-base font-bold">{T.summary}</h2>
              <div className="flex items-center gap-3 border-b pb-4">
                <span className="grid h-9 w-9 place-items-center rounded-lg bg-muted"><Package className="h-4 w-4" /></span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold">{paymentCode}</p>
                  <p className="text-xs text-muted-foreground">{T.oneService}</p>
                </div>
              </div>
              <div className="space-y-4 pt-4 text-sm">
                <div className="flex items-center justify-between gap-3"><span className="text-muted-foreground">{T.subscription}</span><span className="font-semibold">{payment.planCode} · {payment.billingPeriod === 'ANNUAL' ? 'Năm' : 'Tháng'}</span></div>
                <div className="flex items-center justify-between gap-3"><span className="text-muted-foreground">{T.orderStatus}</span><Badge variant="secondary" className="text-[10px]">{T.pending}</Badge></div>
                <div className="flex items-center justify-between gap-3 border-t pt-4"><span className="font-medium text-muted-foreground">{T.total}</span><span className="text-xl font-extrabold">{formatMoneyMinor(payment.amountMinor, payment.currency)}</span></div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-emerald-500/20 bg-emerald-500/5 shadow-sm">
            <CardContent className="space-y-3 p-5 text-xs text-muted-foreground">
              <p className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />{T.verified}</p>
              <p className="flex items-center gap-2"><Clock3 className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />{T.expires}</p>
              <p className="flex items-center gap-2"><LockKeyhole className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />{T.idempotency}</p>
            </CardContent>
          </Card>
        </aside>
      </div>
    </main>
  )
}