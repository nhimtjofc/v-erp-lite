import { useEffect, useMemo, useState } from 'react'
import { Link } from '@tanstack/react-router'
import QRCode from 'qrcode'
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  CreditCard,
  Landmark,
  LoaderCircle,
  LockKeyhole,
  Package,
  QrCode,
  RefreshCw,
  ShieldCheck,
  Store,
  XCircle,
} from 'lucide-react'
import {
  type PaymentIntent,
  useCreateIntentMutation,
  useGetOrderQuery,
  useInitiateChargeMutation,
  useListOrderPaymentIntentsQuery,
  useSimulateTransferredMutation,
} from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

interface OwnerPaymentCheckoutPageProps {
  orderId: string
}

interface PaymentSession {
  transactionId: string
  paymentUrl: string
  qrCodeContent: string
  expiresAt: string
}

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const PAYABLE_ORDER_STATUSES = new Set(['PENDING_PAYMENT'])
const MOCK_PAYMENT_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_MOCK_PAYMENT === 'true'

function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  const candidate = error as { body?: { message?: string; error?: string }; message?: string }
  return candidate?.body?.message ?? candidate?.body?.error ?? candidate?.message ?? 'Không thể xử lý thanh toán. Vui lòng thử lại.'
}

function currentIntent(intents: PaymentIntent[], activeIntentId: string): PaymentIntent | undefined {
  return intents.find((item) => item.status === 'SUCCEEDED')
    ?? intents.find((item) => item.id === activeIntentId)
    ?? intents.find((item) => ['PROCESSING', 'REQUIRES_PAYMENT_METHOD'].includes(item.status?.toUpperCase() ?? ''))
    ?? intents[0]
}

export function OwnerPaymentCheckoutPage({ orderId }: OwnerPaymentCheckoutPageProps) {
  const [session, setSession] = useState<PaymentSession | null>(null)
  const [activeIntentId, setActiveIntentId] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [error, setError] = useState('')
  const [timeLeft, setTimeLeft] = useState('')

  const orderQuery = useGetOrderQuery({ path: { id: orderId } })
  const intentsQuery = useListOrderPaymentIntentsQuery({ path: { orderId } })
  const createIntent = useCreateIntentMutation()
  const initiateCharge = useInitiateChargeMutation()
  const simulateTransferred = useSimulateTransferredMutation()
  const intent = useMemo(() => currentIntent(intentsQuery.data ?? [], activeIntentId), [activeIntentId, intentsQuery.data])
  const status = intent?.status?.toUpperCase()
  const isExpired = Boolean(intent?.expiresAt && ['PROCESSING', 'REQUIRES_PAYMENT_METHOD'].includes(status ?? '') && new Date(intent.expiresAt).getTime() <= Date.now())
  const isBusy = createIntent.isPending || initiateCharge.isPending || simulateTransferred.isPending

  useEffect(() => {
    if (!intent?.id || !status || TERMINAL_STATUSES.has(status)) return
    const intervalId = window.setInterval(() => void intentsQuery.refetch(), 2000)
    return () => window.clearInterval(intervalId)
  }, [intent?.id, intentsQuery, status])

  useEffect(() => {
    if (!session?.expiresAt) return
    const updateTimer = () => {
      const remaining = new Date(session.expiresAt).getTime() - Date.now()
      if (remaining <= 0) {
        setTimeLeft('Đã hết hạn')
        return
      }
      const minutes = Math.floor(remaining / 60000)
      const seconds = Math.floor((remaining % 60000) / 1000)
      setTimeLeft(`${minutes}:${seconds.toString().padStart(2, '0')}`)
    }
    updateTimer()
    const timerId = window.setInterval(updateTimer, 1000)
    return () => window.clearInterval(timerId)
  }, [session?.expiresAt])

  useEffect(() => {
    if (!session?.qrCodeContent) return void setQrDataUrl('')
    void QRCode.toDataURL(session.qrCodeContent, { width: 280, margin: 1 })
      .then(setQrDataUrl)
      .catch(() => setError('Không thể tạo mã QR thanh toán.'))
  }, [session?.qrCodeContent])

  async function beginPayment(forceNew = false) {
    if (!orderId) return
    setError('')
    try {
      let paymentIntent = intent
      const needsNewIntent = forceNew || !paymentIntent || ['FAILED', 'CANCELLED'].includes(paymentIntent.status?.toUpperCase() ?? '') || isExpired
      if (needsNewIntent) {
        paymentIntent = await createIntent.mutateAsync({
          body: { orderId, idempotencyKey: `owner-payment:${orderId}:${crypto.randomUUID()}` },
        }) as PaymentIntent
      }
      if (!paymentIntent?.id) throw new Error('Không thể tạo phiên thanh toán hợp lệ.')
      if (paymentIntent.status === 'SUCCEEDED') return

      setActiveIntentId(paymentIntent.id)
      const charge = await initiateCharge.mutateAsync({
        body: {
          paymentIntentId: paymentIntent.id,
          amountMinor: paymentIntent.amountMinor ?? 0,
          currency: paymentIntent.currency ?? 'VND',
        },
      }) as { transactionId?: string; metadata?: { paymentUrl?: string; qrCodeContent?: string } }
      if (!charge.transactionId) throw new Error('Không thể khởi tạo giao dịch với VietQR Sandbox.')

      setSession({
        transactionId: charge.transactionId,
        paymentUrl: charge.metadata?.paymentUrl ?? `https://mock-gateway.v-erp-lite.local/pay?txn=${charge.transactionId}`,
        qrCodeContent: charge.metadata?.qrCodeContent ?? `v-erp://payment/mock?intent=${paymentIntent.id}&txn=${charge.transactionId}&amount=${paymentIntent.amountMinor}&currency=${paymentIntent.currency}`,
        expiresAt: paymentIntent.expiresAt ?? new Date(Date.now() + 30 * 60 * 1000).toISOString(),
      })
      await intentsQuery.refetch()
    } catch (cause) {
      setError(errorMessage(cause))
    }
  }

  async function sendWebhook(success: boolean) {
    if (!activeIntentId || !session) return
    setError('')
    try {
      await simulateTransferred.mutateAsync({
        body: {
          paymentIntentId: activeIntentId,
          gatewayTransactionId: session.transactionId,
          status: success ? 'SUCCESS' : 'FAILED',
          amountMinor: intent?.amountMinor ?? 0,
          currency: intent?.currency ?? 'VND',
        },
      })
      if (!success) setSession(null)
      await intentsQuery.refetch()
    } catch (cause) {
      setError(errorMessage(cause))
    }
  }

  if (!orderId) return <OwnerPaymentError title="Thiếu mã đơn hàng" message="Không thể mở checkout khi thiếu orderId." />
  if (orderQuery.isLoading || intentsQuery.isLoading) return <div className="flex min-h-[55vh] items-center justify-center gap-3 text-muted-foreground"><LoaderCircle className="h-6 w-6 animate-spin text-primary" />Đang tải checkout của Owner...</div>
  if (orderQuery.error || !orderQuery.data) return <OwnerPaymentError title="Không tìm thấy đơn hàng" message="Đơn hàng không tồn tại hoặc bạn không có quyền truy cập." />
  if (intentsQuery.error) return <OwnerPaymentError title="Không tải được thanh toán" message={errorMessage(intentsQuery.error)} />

  const order = orderQuery.data
  const amount = Number(intent?.amountMinor ?? order.finalAmountMinor ?? 0)
  const currency = intent?.currency ?? order.currency ?? 'VND'

  if (status === 'SUCCEEDED') {
    return <OwnerPaymentResult success orderId={orderId} orderNumber={order.orderNumber} amount={formatMoneyMinor(amount, currency)} />
  }
  if (status === 'FAILED' || status === 'CANCELLED' || isExpired) {
    return <OwnerPaymentResult success={false} orderId={orderId} orderNumber={order.orderNumber} amount={formatMoneyMinor(amount, currency)} error={error} onRetry={MOCK_PAYMENT_ENABLED ? () => void beginPayment(true) : undefined} retrying={isBusy} />
  }
  if (!PAYABLE_ORDER_STATUSES.has(order.status?.toUpperCase() ?? '')) {
    return <OwnerPaymentError title="Đơn hàng không thể thanh toán" message="Chỉ đơn hàng đang chờ thanh toán mới có thể tiếp tục checkout." />
  }

  return (
    <main className="mx-auto max-w-7xl px-4 pb-14 pt-3">
      <div className="mb-7 flex flex-wrap items-center justify-between gap-3 border-b pb-4">
        <Link to="/orders/$id" params={{ id: orderId }} className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />Quay lại đơn hàng</Link>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.45fr)_minmax(19rem,0.85fr)]">
        <section className="space-y-5">
          <div>
            <p className="mb-2 inline-flex items-center gap-1.5 rounded-md bg-emerald-500/10 px-2 py-1 text-xs font-semibold text-emerald-700 dark:text-emerald-400"><ShieldCheck className="h-3.5 w-3.5" />Cổng thanh toán Sandbox bảo mật cao</p>
            <h1 className="text-3xl font-extrabold tracking-tight">Thanh toán đơn hàng</h1>
            <p className="mt-1 text-sm text-muted-foreground">Đơn hàng <span className="font-semibold text-foreground">#{order.orderNumber ?? orderId}</span> đang chờ Owner hoàn tất thanh toán.</p>
          </div>

          {!session ? (
            <Card className="border-border/80 shadow-sm">
              <CardHeader className="pb-3"><CardTitle className="text-base">Phương thức thanh toán</CardTitle><p className="text-sm text-muted-foreground">Chỉ VietQR Sandbox đang khả dụng cho giao dịch Owner.</p></CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-3 rounded-xl border-2 border-primary/70 bg-primary/5 p-3.5"><span className="grid h-11 w-11 place-items-center rounded-lg bg-primary/10 text-primary"><QrCode className="h-5 w-5" /></span><div className="flex-1"><p className="font-semibold">Chuyển khoản VietQR Sandbox</p><p className="mt-0.5 text-xs text-muted-foreground">Tạo mã QR và xác nhận qua webhook mô phỏng có ký HMAC.</p></div><CheckCircle2 className="h-5 w-5 fill-primary text-primary-foreground" /></div>
                <UnavailableMethod icon={Landmark} name="VNPay" />
                <UnavailableMethod icon={CreditCard} name="Thẻ quốc tế (Visa/Mastercard)" />
                {error && <PaymentAlert message={error} />}
                <div className="flex flex-col-reverse gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between"><Link to="/orders/$id" params={{ id: orderId }}><Button variant="ghost">Thanh toán sau</Button></Link><Button onClick={() => void beginPayment()} disabled={isBusy}>{isBusy ? <LoaderCircle className="animate-spin" /> : <LockKeyhole />}Tiếp tục thanh toán</Button></div>
              </CardContent>
            </Card>
          ) : (
            <div className="grid gap-5 md:grid-cols-2">
              <Card className="border-border/80 shadow-sm"><CardHeader className="border-b text-center"><CardTitle className="text-base">Quét mã VietQR để thanh toán</CardTitle></CardHeader><CardContent className="space-y-4 py-6 text-center"><div className="mx-auto w-fit rounded-2xl border bg-white p-3">{qrDataUrl ? <img src={qrDataUrl} alt="Mã VietQR thanh toán" className="h-56 w-56" /> : <LoaderCircle className="m-24 h-8 w-8 animate-spin" />}</div><p className="text-3xl font-extrabold text-primary">{formatMoneyMinor(amount, currency)}</p><p className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-700 dark:text-emerald-400"><RefreshCw className="h-3.5 w-3.5 animate-spin" />Đang chờ xác nhận giao dịch</p></CardContent></Card>
              <Card className="border-slate-800 bg-slate-950 text-slate-100 shadow-sm"><CardHeader className="border-b border-slate-800"><CardTitle className="font-mono text-sm text-emerald-400">VIETQR WEBHOOK SIMULATOR</CardTitle></CardHeader><CardContent className="space-y-4 pt-5"><div className="space-y-2 rounded-lg border border-slate-800 bg-slate-900/70 p-3 font-mono text-xs text-slate-300"><p><span className="text-slate-500">ORDER:</span> {order.orderNumber ?? orderId}</p><p><span className="text-slate-500">TRANSACTION:</span> {session.transactionId}</p><p><span className="text-slate-500">EXPIRES IN:</span> <span className="text-amber-300">{timeLeft}</span></p><p className="truncate text-slate-500" title={session.paymentUrl}>URL: {session.paymentUrl}</p></div>{error && <PaymentAlert message={error} />}<div className="grid gap-2"><Button onClick={() => void sendWebhook(true)} disabled={isBusy} className="bg-emerald-600 hover:bg-emerald-500"><CheckCircle2 />Gửi webhook thành công</Button><Button onClick={() => void sendWebhook(false)} disabled={isBusy} variant="destructive"><XCircle />Gửi webhook thất bại</Button></div></CardContent></Card>
            </div>
          )}
        </section>

        <aside className="space-y-5">
          <Card className="border-border/80 shadow-sm"><CardContent className="p-5"><h2 className="mb-4 text-base font-bold">Tóm tắt đơn hàng</h2><div className="flex items-center gap-3 border-b pb-4"><span className="grid h-10 w-10 place-items-center rounded-lg bg-muted"><Package className="h-5 w-5" /></span><div><p className="text-sm font-bold">{order.orderNumber ?? orderId}</p><p className="text-xs text-muted-foreground">{order.items?.length ?? 0} dòng sản phẩm</p></div></div><div className="space-y-4 pt-4 text-sm"><div className="flex justify-between gap-3"><span className="text-muted-foreground">Trạng thái đơn</span><Badge variant="secondary">{order.status}</Badge></div><div className="flex justify-between gap-3 border-t pt-4"><span className="font-medium text-muted-foreground">Tổng thanh toán</span><span className="text-xl font-extrabold">{formatMoneyMinor(amount, currency)}</span></div></div></CardContent></Card>
          <Card className="border-emerald-500/20 bg-emerald-500/5 shadow-sm"><CardContent className="space-y-3 p-5 text-xs text-muted-foreground"><TrustLine icon={ShieldCheck} text="Xác thực chữ ký webhook và số dư giao dịch" /><TrustLine icon={Clock3} text="Phiên thanh toán được bảo vệ trong 30 phút" /><TrustLine icon={LockKeyhole} text="Idempotency ngăn giao dịch bị xử lý trùng lặp" /></CardContent></Card>
        </aside>
      </div>
    </main>
  )
}

function UnavailableMethod({ icon: Icon, name }: { icon: typeof Landmark; name: string }) {
  return <div className="flex items-center gap-3 rounded-xl border border-dashed border-border bg-muted/20 p-3.5 text-muted-foreground/70" aria-disabled="true"><span className="grid h-11 w-11 place-items-center rounded-lg bg-muted"><Icon className="h-5 w-5" /></span><span className="flex-1 text-sm font-medium">{name}</span><Badge variant="secondary" className="text-[10px]">Sắp ra mắt</Badge></div>
}

function TrustLine({ icon: Icon, text }: { icon: typeof ShieldCheck; text: string }) {
  return <p className="flex items-center gap-2"><Icon className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />{text}</p>
}

function PaymentAlert({ message }: { message: string }) {
  return <p className="flex items-center gap-2 rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive"><AlertCircle className="h-4 w-4 shrink-0" />{message}</p>
}

function OwnerPaymentError({ title, message }: { title: string; message: string }) {
  return <div className="mx-auto max-w-lg py-20 text-center"><AlertCircle className="mx-auto mb-4 h-12 w-12 text-destructive" /><h1 className="text-2xl font-bold">{title}</h1><p className="mt-2 text-sm text-muted-foreground">{message}</p><Link to="/orders" className="mt-6 inline-block"><Button variant="outline">Quay lại đơn hàng</Button></Link></div>
}

function OwnerPaymentResult({ success, orderId, orderNumber, amount, error, onRetry, retrying }: { success: boolean; orderId: string; orderNumber?: string; amount: string; error?: string; onRetry?: () => void; retrying?: boolean }) {
  return <div className="mx-auto max-w-xl py-14"><Card className="overflow-hidden text-center shadow-lg"><div className={success ? 'h-1.5 bg-emerald-500' : 'h-1.5 bg-destructive'} /><CardContent className="space-y-5 px-6 py-10">{success ? <CheckCircle2 className="mx-auto h-14 w-14 text-emerald-500" /> : <XCircle className="mx-auto h-14 w-14 text-destructive" />}<div><h1 className="text-2xl font-bold">{success ? 'Thanh toán thành công' : 'Thanh toán thất bại'}</h1><p className="mt-2 text-sm text-muted-foreground">{success ? `Đã ghi nhận ${amount} cho đơn hàng #${orderNumber ?? orderId}.` : 'Đơn hàng vẫn đang chờ thanh toán. Bạn có thể tạo lại một phiên mới.'}</p></div>{error && <PaymentAlert message={error} />}<div className="flex flex-col justify-center gap-2 sm:flex-row">{!success && onRetry && <Button onClick={onRetry} disabled={retrying}>{retrying && <LoaderCircle className="animate-spin" />}Tạo thanh toán mới</Button>}<Link to="/orders/$id" params={{ id: orderId }}><Button variant={success ? 'default' : 'outline'}>Xem đơn hàng</Button></Link></div></CardContent></Card></div>
}