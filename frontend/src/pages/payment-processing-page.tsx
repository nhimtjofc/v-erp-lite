import { useEffect, useMemo, useState, type ElementType } from 'react'
import { Link } from '@tanstack/react-router'
import QRCode from 'qrcode'
import {
  type PaymentIntent,
  useCreateIntentMutation,
  useGetOrderQuery,
  useInitiateChargeMutation,
  useListOrderPaymentIntentsQuery,
  useSimulateTransferredMutation,
} from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Button } from '@/shared/ui/button'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { cn } from '@/shared/lib/cn'
import {
  AlertCircle,
  ArrowLeft,
  Check,
  CheckCircle2,
  Clock3,
  CreditCard,
  Landmark,
  LoaderCircle,
  LockKeyhole,
  PackageCheck,
  QrCode,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  XCircle,
  Terminal,
  Server,
  Zap,
} from 'lucide-react'

interface PaymentProcessingPageProps {
  orderId: string
}

interface MockPaymentSessionResponse {
  gateway: string
  gatewayTransactionId: string
  paymentUrl: string
  qrCodeContent: string
  expiresAt: string
}

type PaymentMethod = 'MOCK_QR'

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const PAYABLE_ORDER_STATUSES = new Set(['PENDING', 'PENDING_PAYMENT'])
const MOCK_PAYMENT_ENABLED = import.meta.env.DEV
  || import.meta.env.VITE_ENABLE_MOCK_PAYMENT === 'true'

function paymentErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  const candidate = error as {
    body?: { message?: string; error?: string }
    message?: string
  }
  return candidate?.body?.message
    ?? candidate?.body?.error
    ?? candidate?.message
    ?? 'Không thể xử lý thanh toán. Vui lòng thử lại.'
}

function effectivePaymentIntent(intents: PaymentIntent[], activeIntentId: string): PaymentIntent | undefined {
  return intents.find((intent) => intent.status === 'SUCCEEDED')
    ?? intents.find((intent) => intent.id === activeIntentId)
    ?? intents.find((intent) => {
      const status = intent.status?.toUpperCase()
      const expiresAt = intent.expiresAt ? new Date(intent.expiresAt).getTime() : Number.POSITIVE_INFINITY
      return ['PROCESSING', 'REQUIRES_PAYMENT_METHOD'].includes(status ?? '')
        && expiresAt > Date.now()
    })
    ?? intents[0]
}

export function PaymentProcessingPage({ orderId }: PaymentProcessingPageProps) {
  const [method, setMethod] = useState<PaymentMethod>('MOCK_QR')
  const [session, setSession] = useState<MockPaymentSessionResponse | null>(null)
  const [activeIntentId, setActiveIntentId] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [actionError, setActionError] = useState('')
  const [timeLeft, setTimeLeft] = useState<string>('')

  const orderQuery = useGetOrderQuery({ path: { id: orderId } })
  const intentsQuery = useListOrderPaymentIntentsQuery({ path: { orderId } })
  const createIntentMutation = useCreateIntentMutation()
  const initiateChargeMutation = useInitiateChargeMutation()
  const simulateTransferredMutation = useSimulateTransferredMutation()

  const intents = intentsQuery.data ?? []
  const intent = useMemo(
    () => effectivePaymentIntent(intents, activeIntentId),
    [activeIntentId, intents],
  )
  const status = intent?.status?.toUpperCase()
  const isExpired = Boolean(
    ['REQUIRES_PAYMENT_METHOD', 'PROCESSING'].includes(status ?? '')
      && intent?.expiresAt
      && new Date(intent.expiresAt).getTime() <= Date.now()
  )
  const isBusy = createIntentMutation.isPending
    || initiateChargeMutation.isPending
    || simulateTransferredMutation.isPending

  // Polling active payment intent
  useEffect(() => {
    if (!intent?.id || !status || TERMINAL_STATUSES.has(status)) return
    const intervalId = window.setInterval(() => {
      void intentsQuery.refetch()
    }, 2000)
    return () => window.clearInterval(intervalId)
  }, [intent?.id, status, intentsQuery.refetch])

  // Timer countdown for intent expiry
  useEffect(() => {
    if (!intent?.expiresAt || TERMINAL_STATUSES.has(status ?? '')) {
      setTimeLeft('')
      return
    }

    const updateTimer = () => {
      const remaining = new Date(intent.expiresAt!).getTime() - Date.now()
      if (remaining <= 0) {
        setTimeLeft('Đã hết hạn')
        void intentsQuery.refetch()
      } else {
        const mins = Math.floor(remaining / 60000)
        const secs = Math.floor((remaining % 60000) / 1000)
        setTimeLeft(`${mins}:${secs < 10 ? '0' : ''}${secs}`)
      }
    }

    updateTimer()
    const timerId = window.setInterval(updateTimer, 1000)
    return () => window.clearInterval(timerId)
  }, [intent?.expiresAt, status, intentsQuery])

  // Generate QR Code URL
  useEffect(() => {
    if (!session?.qrCodeContent) {
      setQrDataUrl('')
      return
    }
    QRCode.toDataURL(session.qrCodeContent, { width: 260, margin: 1 })
      .then(setQrDataUrl)
      .catch(() => setActionError('Không thể tạo mã QR thanh toán.'))
  }, [session?.qrCodeContent])

  async function startPayment(forceNew = false) {
    if (!orderId) return
    setActionError('')
    setSession(null)

    try {
      let payableIntent = intent
      const currentStatus = payableIntent?.status?.toUpperCase()
      if (forceNew || !payableIntent || ['FAILED', 'CANCELLED'].includes(currentStatus ?? '') || isExpired) {
        payableIntent = await createIntentMutation.mutateAsync({
          body: {
            orderId,
            idempotencyKey: `payment:${orderId}:${crypto.randomUUID()}`,
          },
        }) as PaymentIntent
      }

      if (payableIntent.status === 'SUCCEEDED') {
        await intentsQuery.refetch()
        return
      }
      if (!payableIntent.id) {
        throw new Error('Backend không trả về PaymentIntent hợp lệ.')
      }
      setActiveIntentId(payableIntent.id)

      // Initiate charge with mock bank API
      const opResult = await initiateChargeMutation.mutateAsync({
        body: {
          paymentIntentId: payableIntent.id,
          amountMinor: payableIntent.amountMinor!,
          currency: payableIntent.currency!,
        },
      }) as any

      if (opResult && opResult.transactionId) {
        setSession({
          gateway: 'MOCK',
          gatewayTransactionId: opResult.transactionId,
          paymentUrl: opResult.metadata.paymentUrl || `https://mock-gateway.v-erp-lite.local/pay?txn=${opResult.transactionId}`,
          qrCodeContent: opResult.metadata.qrCodeContent || `v-erp://payment/mock?intent=${payableIntent.id}&txn=${opResult.transactionId}&amount=${payableIntent.amountMinor}&currency=${payableIntent.currency}`,
          expiresAt: payableIntent.expiresAt ?? new Date(Date.now() + 30 * 60 * 1000).toISOString(),
        })
      } else {
        throw new Error('Không thể khởi tạo phiên giao dịch từ Mock Bank.')
      }

      await intentsQuery.refetch()
    } catch (error) {
      setActionError(paymentErrorMessage(error))
    }
  }

  async function simulatePaymentWebhook(success: boolean) {
    if (!activeIntentId || !session?.gatewayTransactionId) return
    setActionError('')
    try {
      await simulateTransferredMutation.mutateAsync({
        body: {
          paymentIntentId: activeIntentId,
          gatewayTransactionId: session.gatewayTransactionId,
          status: success ? 'SUCCESS' : 'FAILED',
          amountMinor: intent?.amountMinor ?? 0,
          currency: intent?.currency ?? 'VND',
        },
      })
      if (!success) {
        setSession(null)
      }
      await intentsQuery.refetch()
    } catch (error) {
      setActionError(paymentErrorMessage(error))
    }
  }

  if (!orderId) {
    return <PaymentError title="Thiếu mã đơn hàng" message="Không thể mở trang thanh toán khi thiếu orderId." />
  }

  if (orderQuery.isLoading || intentsQuery.isLoading) {
    return (
      <div className="flex min-h-[55vh] items-center justify-center gap-3 text-muted-foreground">
        <LoaderCircle className="h-6 w-6 animate-spin text-primary" />
        Đang tải phiên thanh toán an toàn...
      </div>
    )
  }

  if (orderQuery.error || !orderQuery.data) {
    return <PaymentError title="Không tìm thấy đơn hàng" message="Đơn hàng không tồn tại hoặc bạn không có quyền truy cập." />
  }

  if (intentsQuery.error) {
    return <PaymentError title="Không tải được thanh toán" message={paymentErrorMessage(intentsQuery.error)} />
  }

  const order = orderQuery.data
  const currency = order.currency ?? 'USD'
  const amount = Number(order.finalAmountMinor ?? 0)
  const paymentCurrency = intent?.currency ?? currency
  const paymentAmount = Number(intent?.amountMinor ?? amount)

  if (status === 'SUCCEEDED') {
    return (
      <PaymentResult
        success
        orderId={orderId}
        orderNumber={order.orderNumber}
        amount={formatMoneyMinor(paymentAmount, paymentCurrency)}
      />
    )
  }

  if (status === 'FAILED' || status === 'CANCELLED' || isExpired) {
    return (
      <PaymentResult
        success={false}
        orderId={orderId}
        orderNumber={order.orderNumber}
        amount={formatMoneyMinor(paymentAmount, paymentCurrency)}
        expired={isExpired}
        error={actionError}
        retrying={isBusy}
        onRetry={MOCK_PAYMENT_ENABLED && PAYABLE_ORDER_STATUSES.has(order.status?.toUpperCase() ?? '')
          ? () => void startPayment(true)
          : undefined}
      />
    )
  }

  if (!PAYABLE_ORDER_STATUSES.has(order.status?.toUpperCase() ?? '')) {
    return (
      <PaymentError
        title="Đơn hàng không thể thanh toán"
        message="Chỉ đơn hàng đang chờ thanh toán mới có thể bắt đầu hoặc hoàn tất thanh toán."
      />
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 pb-16 pt-2">
      {/* Steps progress indicator */}
      <div className="flex items-center justify-between border-b pb-4">
        <Link
          to="/orders/$id"
          params={{ id: orderId }}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4" /> Quay lại đơn hàng
        </Link>
        <div className="hidden sm:flex items-center gap-3 text-xs font-semibold tracking-wider text-muted-foreground">
          <span className="text-primary font-bold">1. ĐƠN HÀNG</span>
          <span className="text-muted-foreground/30">/</span>
          <span className={cn(session ? "text-primary font-bold" : "text-muted-foreground")}>2. XÁC NHẬN</span>
          <span className="text-muted-foreground/30">/</span>
          <span className={cn(status === 'SUCCEEDED' ? "text-primary font-bold" : "text-muted-foreground")}>3. HOÀN TẤT</span>
        </div>
      </div>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1.5fr)_minmax(320px,0.85fr)]">
        <div className="space-y-6">
          <div>
            <Badge variant="outline" className="mb-2 border-emerald-500/20 bg-emerald-500/5 text-emerald-600 dark:text-emerald-400 font-medium">
              <Zap className="mr-1 h-3 w-3 fill-current" /> Cổng thanh toán Sandbox Bảo Mật Cao
            </Badge>
            <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-foreground to-foreground/75 bg-clip-text">
              Hoàn tất thanh toán
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Đơn hàng <span className="font-mono text-foreground font-semibold">#{order.orderNumber}</span> của bạn đã sẵn sàng.
            </p>
          </div>

          {!session ? (
            <Card className="border-border/60 shadow-lg dark:shadow-black/20 overflow-hidden bg-card/50 backdrop-blur-md">
              <CardHeader>
                <CardTitle>Chọn phương thức thanh toán</CardTitle>
                <CardDescription>Cổng thanh toán giả lập hỗ trợ kiểm tra luồng bảo mật HMAC & Replay Protection.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <button
                  type="button"
                  onClick={() => setMethod('MOCK_QR')}
                  className={cn(
                    'flex w-full items-center gap-4 rounded-xl border p-4 text-left transition-all duration-200',
                    method === 'MOCK_QR'
                      ? 'border-primary bg-primary/5 ring-2 ring-primary/20 shadow-md translate-x-1'
                      : 'border-border/60 hover:bg-muted/30 hover:border-border'
                  )}
                >
                  <span className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10 text-primary shrink-0">
                    <QrCode className="h-6 w-6" />
                  </span>
                  <span className="flex-1">
                    <span className="block font-bold text-foreground">Chuyển khoản VietQR Sandbox</span>
                    <span className="block text-xs text-muted-foreground mt-0.5">Xác thực giao dịch tự động qua Webhook ký mã HMAC</span>
                  </span>
                  <span className={cn(
                    'flex h-6 w-6 items-center justify-center rounded-full border transition-all',
                    method === 'MOCK_QR' ? 'border-primary bg-primary text-primary-foreground scale-105' : 'border-muted-foreground/30'
                  )}>
                    {method === 'MOCK_QR' && <Check className="h-4 w-4" />}
                  </span>
                </button>

                <UnavailableMethod icon={Landmark} name="VNPay" />
                <UnavailableMethod icon={CreditCard} name="Thẻ quốc tế (Visa/Mastercard)" />

                {actionError && <PaymentAlert message={actionError} />}

                <div className="flex flex-col-reverse gap-3 pt-4 sm:flex-row sm:justify-between items-center border-t border-border/40">
                  <Link to="/shop">
                    <Button variant="ghost" className="w-full sm:w-auto">Tiếp tục mua sắm · Thanh toán sau</Button>
                  </Link>
                  <Button onClick={() => void startPayment()} disabled={isBusy} className="w-full sm:w-auto sm:min-w-48 shadow-lg shadow-primary/10">
                    {isBusy ? <LoaderCircle className="animate-spin h-4 w-4" /> : <LockKeyhole className="h-4 w-4" />}
                    {status === 'PROCESSING' ? 'Tạo lại mã QR' : 'Tiếp tục thanh toán'}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="grid gap-6 md:grid-cols-[1fr_1.1fr]">
              {/* QR Code Card */}
              <Card className="border-border/60 shadow-xl overflow-hidden bg-card/60 backdrop-blur-md flex flex-col justify-between">
                <CardHeader className="border-b bg-muted/20 text-center py-4">
                  <div className="mx-auto mb-1 flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <Smartphone className="h-5 w-5" />
                  </div>
                  <CardTitle className="text-lg">Quét mã QR thanh toán</CardTitle>
                  <CardDescription className="text-xs">Ứng dụng tự động kiểm tra trạng thái</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4 pt-6 text-center flex-1 flex flex-col justify-center">
                  <div className="relative mx-auto w-fit rounded-2xl border bg-white p-3 shadow-md group">
                    {qrDataUrl ? (
                      <img src={qrDataUrl} alt="Mã QR thanh toán VietQR" className="h-52 w-52 transition-transform duration-300 group-hover:scale-[1.02]" />
                    ) : (
                      <div className="flex h-52 w-52 items-center justify-center">
                        <LoaderCircle className="h-8 w-8 animate-spin text-muted-foreground" />
                      </div>
                    )}
                    {/* Glowing effect inside QR card */}
                    <div className="absolute inset-0 rounded-2xl border-2 border-primary/20 pointer-events-none animate-pulse" />
                  </div>

                  <div className="space-y-1">
                    <div className="text-xs text-muted-foreground font-medium uppercase tracking-wider">Số tiền cần chuyển</div>
                    <div className="text-3xl font-extrabold text-primary tracking-tight">
                      {formatMoneyMinor(paymentAmount, paymentCurrency)}
                    </div>
                  </div>

                  <div className="flex items-center justify-center gap-2 text-xs font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-500/5 py-1.5 px-3 rounded-full w-fit mx-auto border border-emerald-500/10">
                    <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                    Đang lắng nghe tín hiệu ngân hàng...
                  </div>
                </CardContent>
              </Card>

              {/* Developer Sandbox POS Console */}
              <Card className="border-primary/20 shadow-xl overflow-hidden bg-slate-950 text-slate-100 flex flex-col justify-between font-mono">
                <div className="bg-slate-900 border-b border-slate-800 px-4 py-3 flex items-center justify-between text-xs text-slate-400">
                  <div className="flex items-center gap-2 font-bold text-slate-200">
                    <Terminal className="h-4 w-4 text-emerald-400" />
                    <span>MOCK BANK POS SIMULATOR v2.0</span>
                  </div>
                  <Badge variant="outline" className="text-[10px] border-emerald-500/30 text-emerald-400 bg-emerald-500/10 font-mono">
                    ONLINE
                  </Badge>
                </div>
                <CardContent className="space-y-4 pt-4 text-xs flex-1 flex flex-col justify-between">
                  <div className="space-y-2 bg-slate-900/60 border border-slate-800 rounded-lg p-3 text-[11px] leading-relaxed text-slate-300">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-1.5 mb-1.5 text-slate-400">
                      <Server className="h-3.5 w-3.5 text-blue-400" />
                      <span>Security Inspection & Webhook Metadata</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-slate-500">GATEWAY:</span>
                      <span className="text-emerald-400 font-bold">{session.gateway}</span>
                    </div>
                    <div className="flex items-center justify-between gap-4">
                      <span className="text-slate-500 shrink-0">TXN_ID:</span>
                      <span className="truncate text-blue-300 select-all" title={session.gatewayTransactionId}>
                        {session.gatewayTransactionId}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-slate-500">EXPIRES:</span>
                      <span className="text-amber-400">{timeLeft ? timeLeft : 'N/A'}</span>
                    </div>
                    <div className="border-t border-slate-800 pt-1.5 mt-1.5">
                      <span className="text-slate-500 block mb-1">MOCK URL (Signed webhook emitter):</span>
                      <span className="block truncate text-slate-400 text-[10px]" title={session.paymentUrl}>
                        {session.paymentUrl}
                      </span>
                    </div>
                  </div>

                  <div className="border border-slate-800 rounded-lg p-3 bg-slate-900/40 text-[11px] space-y-1">
                    <div className="text-slate-400 font-bold mb-1 flex items-center gap-1">
                      <ShieldCheck className="h-3.5 w-3.5 text-emerald-400" /> Webhook Security (HMAC Verified)
                    </div>
                    <p className="text-slate-500 text-[10px]">
                      Bản tin webhook sẽ được ký bằng HMAC-SHA256 thô thông qua khóa bí mật trước khi gửi. Database sẽ kiểm tra tính duy nhất của mã giao dịch để chặn Replay Attack.
                    </p>
                  </div>

                  {actionError && <PaymentAlert message={actionError} />}

                  <div className="space-y-3 pt-2">
                    <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">
                      MÔ PHỎNG PHẢN HỒI WEBHOOK TỪ NGÂN HÀNG
                    </div>
                    <div className="flex flex-col gap-2">
                      <Button
                        onClick={() => void simulatePaymentWebhook(true)}
                        disabled={isBusy}
                        className="bg-emerald-600 hover:bg-emerald-500 text-white border-0 font-mono text-xs shadow-lg shadow-emerald-950/20"
                      >
                        {simulateTransferredMutation.isPending ? (
                          <LoaderCircle className="animate-spin h-3.5 w-3.5 mr-1" />
                        ) : (
                          <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                        )}
                        Gửi Webhook SUCCESS (200)
                      </Button>
                      <Button
                        variant="destructive"
                        onClick={() => void simulatePaymentWebhook(false)}
                        disabled={isBusy}
                        className="bg-rose-700 hover:bg-rose-600 font-mono text-xs shadow-lg shadow-rose-950/20"
                      >
                        {simulateTransferredMutation.isPending ? (
                          <LoaderCircle className="animate-spin h-3.5 w-3.5 mr-1" />
                        ) : (
                          <XCircle className="h-3.5 w-3.5 mr-1" />
                        )}
                        Gửi Webhook FAILED (400)
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          )}
        </div>

        {/* Order Summary Sidebar */}
        <div className="space-y-6">
          <Card className="border-border/60 shadow-lg bg-card/30 backdrop-blur-md">
            <CardHeader className="pb-3">
              <CardTitle className="text-lg">Tóm tắt đơn hàng</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <PackageCheck className="h-5 w-5" />
                </div>
                <div>
                  <div className="font-bold text-sm text-foreground">{order.orderNumber}</div>
                  <div className="text-xs text-muted-foreground">{order.items?.length ?? 0} dòng sản phẩm</div>
                </div>
              </div>
              <div className="border-t border-border/40 pt-4 space-y-2">
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>Trạng thái đơn</span>
                  <Badge variant="secondary">{order.status}</Badge>
                </div>
                <div className="flex items-center justify-between text-sm pt-2">
                  <span className="text-muted-foreground font-medium">Tổng thanh toán</span>
                  <span className="text-xl font-extrabold text-foreground tracking-tight">
                    {formatMoneyMinor(paymentAmount, paymentCurrency)}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border/60 shadow-sm bg-card/10 backdrop-blur-sm">
            <CardContent className="space-y-3.5 pt-6 text-xs leading-relaxed">
              <TrustItem icon={ShieldCheck} text="Hệ thống xác thực chéo số dư & chữ ký số" />
              <TrustItem icon={Clock3} text="Phiên giao dịch được khóa bảo vệ trong 30 phút" />
              <TrustItem icon={LockKeyhole} text="Idempotency ngăn chặn nạp tiền trùng lặp tuyệt đối" />
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

function UnavailableMethod({ icon: Icon, name }: { icon: ElementType; name: string }) {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-dashed border-border/50 p-4 opacity-50 bg-muted/10 cursor-not-allowed">
      <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="h-5 w-5" />
      </span>
      <span className="flex-1 font-semibold text-sm text-muted-foreground">{name}</span>
      <Badge variant="secondary" className="text-[10px]">Sắp ra mắt</Badge>
    </div>
  )
}

function TrustItem({ icon: Icon, text }: { icon: ElementType; text: string }) {
  return (
    <div className="flex items-start gap-2.5">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
      <span className="text-muted-foreground font-medium">{text}</span>
    </div>
  )
}

function PaymentAlert({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2.5 rounded-lg border border-destructive/20 bg-destructive/5 p-3.5 text-left text-xs text-destructive">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <span className="font-semibold">{message}</span>
    </div>
  )
}

function PaymentError({ title, message }: { title: string; message: string }) {
  return (
    <div className="mx-auto max-w-lg py-20 px-4 text-center">
      <AlertCircle className="mx-auto mb-4 h-14 w-14 text-destructive animate-bounce" />
      <h1 className="text-2xl font-extrabold text-foreground tracking-tight">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Link to="/orders" className="mt-6 inline-block">
        <Button variant="outline"><ArrowLeft className="mr-1.5 h-4 w-4" /> Quay lại danh sách đơn</Button>
      </Link>
    </div>
  )
}

interface PaymentResultProps {
  success: boolean
  orderId: string
  orderNumber?: string
  amount: string
  expired?: boolean
  error?: string
  retrying?: boolean
  onRetry?: () => void
}

function PaymentResult({
  success,
  orderId,
  orderNumber,
  amount,
  expired,
  error,
  retrying,
  onRetry,
}: PaymentResultProps) {
  return (
    <div className="mx-auto max-w-xl py-12 px-4">
      <Card className="overflow-hidden text-center border-border/60 shadow-2xl bg-card/60 backdrop-blur-md">
        <div className={cn('h-2.5', success ? 'bg-emerald-500' : 'bg-rose-500')} />
        <CardContent className="space-y-6 px-6 py-12">
          <div className={cn(
            'mx-auto flex h-20 w-20 items-center justify-center rounded-full shadow-inner transition-transform duration-500 hover:scale-105',
            success ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'
          )}>
            {success ? <CheckCircle2 className="h-10 w-10" /> : <XCircle className="h-10 w-10" />}
          </div>
          <div className="space-y-2">
            <h1 className="text-3xl font-extrabold tracking-tight">
              {success ? 'Thanh toán thành công' : expired ? 'Phiên thanh toán đã hết hạn' : 'Thanh toán thất bại'}
            </h1>
            <p className="text-sm text-muted-foreground leading-relaxed px-4">
              {success
                ? `Đã ghi nhận thanh toán số tiền ${amount} cho đơn hàng #${orderNumber ?? orderId}. Đơn hàng đang được hệ thống xử lý.`
                : 'Thanh toán không thể hoàn tất. Đơn hàng vẫn được giữ trong danh sách chờ để bạn tiếp tục thanh toán lại.'}
            </p>
          </div>

          {error && <PaymentAlert message={error} />}

          <div className="flex flex-col justify-center gap-3 sm:flex-row pt-4 border-t border-border/40">
            {!success && onRetry && (
              <Button onClick={onRetry} disabled={retrying} className="w-full sm:w-auto">
                {retrying ? <LoaderCircle className="animate-spin mr-1.5 h-4 w-4" /> : <RefreshCw className="mr-1.5 h-4 w-4" />}
                Thử thanh toán lại
              </Button>
            )}
            <Link to="/orders/$id" params={{ id: orderId }} className="w-full sm:w-auto">
              <Button variant={success ? 'default' : 'outline'} className="w-full">
                Xem chi tiết đơn hàng
              </Button>
            </Link>
            {success && (
              <Link to="/shop" className="w-full sm:w-auto">
                <Button variant="outline" className="w-full">Tiếp tục mua sắm</Button>
              </Link>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
