import { useEffect, useMemo, useState, type ElementType } from 'react'
import { Link } from '@tanstack/react-router'
import QRCode from 'qrcode'
import {
  type PaymentIntent,
  useCompleteMockPaymentMutation,
  useCreateIntentMutation,
  useFailMockPaymentMutation,
  useGetOrderQuery,
  useInitiateMockPaymentMutation,
  useListOrderPaymentIntentsQuery,
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

  const orderQuery = useGetOrderQuery({ path: { id: orderId } })
  const intentsQuery = useListOrderPaymentIntentsQuery({ path: { orderId } })
  const createIntentMutation = useCreateIntentMutation()
  const initiateMutation = useInitiateMockPaymentMutation()
  const completeMutation = useCompleteMockPaymentMutation()
  const failMutation = useFailMockPaymentMutation()

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
    || initiateMutation.isPending
    || completeMutation.isPending
    || failMutation.isPending

  useEffect(() => {
    if (!intent?.id || !status || TERMINAL_STATUSES.has(status)) return
    const intervalId = window.setInterval(() => {
      void intentsQuery.refetch()
    }, 2000)
    return () => window.clearInterval(intervalId)
  }, [intent?.id, status, intentsQuery.refetch])

  useEffect(() => {
    if (!session?.qrCodeContent) {
      setQrDataUrl('')
      return
    }
    QRCode.toDataURL(session.qrCodeContent, { width: 240, margin: 2 })
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

      const createdSession = await initiateMutation.mutateAsync({
        body: { paymentIntentId: payableIntent.id },
      }) as MockPaymentSessionResponse
      setSession(createdSession)
      await intentsQuery.refetch()
    } catch (error) {
      setActionError(paymentErrorMessage(error))
    }
  }

  async function completePayment() {
    if (!activeIntentId) return
    setActionError('')
    try {
      await completeMutation.mutateAsync({
        path: { intentId: activeIntentId },
      })
      await intentsQuery.refetch()
    } catch (error) {
      setActionError(paymentErrorMessage(error))
    }
  }

  async function failPayment() {
    if (!activeIntentId) return
    setActionError('')
    try {
      await failMutation.mutateAsync({
        path: { intentId: activeIntentId },
      })
      setSession(null)
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
        <LoaderCircle className="h-6 w-6 animate-spin" />
        Đang tải phiên thanh toán...
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

  if (!MOCK_PAYMENT_ENABLED) {
    return (
      <PaymentError
        title="Cổng thanh toán demo đã tắt"
        message="MockPay chỉ được bật trong môi trường phát triển và kiểm thử."
      />
    )
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 pb-12">
      <Link
        to="/orders/$id"
        params={{ id: orderId }}
        className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Quay lại đơn hàng
      </Link>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.45fr)_minmax(300px,0.75fr)]">
        <div className="space-y-6">
          <div>
            <Badge variant="outline" className="mb-3 border-primary/30 bg-primary/5 text-primary">
              Thanh toán an toàn
            </Badge>
            <h1 className="text-3xl font-bold tracking-tight">Hoàn tất thanh toán</h1>
            <p className="mt-2 text-muted-foreground">
              Đơn {order.orderNumber} đã được tạo. Chọn phương thức để tiếp tục.
            </p>
          </div>

          {!session ? (
            <Card>
              <CardHeader>
                <CardTitle>Phương thức thanh toán</CardTitle>
                <CardDescription>Gateway mock dùng cho môi trường demo, không trừ tiền thật.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <button
                  type="button"
                  onClick={() => setMethod('MOCK_QR')}
                  className={cn(
                    'flex w-full items-center gap-4 rounded-xl border p-4 text-left transition-colors',
                    method === 'MOCK_QR'
                      ? 'border-primary bg-primary/5 ring-1 ring-primary'
                      : 'border-border hover:bg-muted/50'
                  )}
                >
                  <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <QrCode className="h-5 w-5" />
                  </span>
                  <span className="flex-1">
                    <span className="block font-semibold">Chuyển khoản VietQR</span>
                    <span className="block text-sm text-muted-foreground">Quét mã VietQR bằng ứng dụng ngân hàng</span>
                  </span>
                  <span className={cn(
                    'flex h-5 w-5 items-center justify-center rounded-full border',
                    method === 'MOCK_QR' ? 'border-primary bg-primary text-primary-foreground' : 'border-border'
                  )}>
                    {method === 'MOCK_QR' && <Check className="h-3.5 w-3.5" />}
                  </span>
                </button>

                <UnavailableMethod icon={Landmark} name="VNPay" />
                <UnavailableMethod icon={CreditCard} name="Thẻ quốc tế" />

                {actionError && <PaymentAlert message={actionError} />}

                <div className="flex flex-col-reverse gap-3 pt-3 sm:flex-row sm:justify-between">
                  <Link to="/shop">
                    <Button variant="ghost" className="w-full sm:w-auto">Tiếp tục mua sắm · Thanh toán sau</Button>
                  </Link>
                  <Button onClick={() => void startPayment()} disabled={isBusy} className="sm:min-w-44">
                    {isBusy ? <LoaderCircle className="animate-spin" /> : <LockKeyhole />}
                    {status === 'PROCESSING' ? 'Tạo lại mã QR' : 'Tiếp tục thanh toán'}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <Card className="overflow-hidden">
              <CardHeader className="border-b bg-muted/30 text-center">
                <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <Smartphone className="h-6 w-6" />
                </div>
                <CardTitle>Quét mã để thanh toán</CardTitle>
                <CardDescription>PaymentIntent đang được polling mỗi 2 giây.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-5 pt-6 text-center">
                <div className="mx-auto w-fit rounded-2xl border bg-white p-3 shadow-sm">
                  {qrDataUrl ? (
                    <img src={qrDataUrl} alt="Mã QR thanh toán MockPay" className="h-56 w-56" />
                  ) : (
                    <div className="flex h-56 w-56 items-center justify-center">
                      <LoaderCircle className="h-8 w-8 animate-spin text-muted-foreground" />
                    </div>
                  )}
                </div>

                <div>
                  <div className="text-sm text-muted-foreground">Số tiền</div>
                  <div className="text-2xl font-bold text-primary">{formatMoneyMinor(paymentAmount, paymentCurrency)}</div>
                </div>

                <div className="space-y-2 rounded-lg border bg-muted/30 p-4 text-left text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-muted-foreground">Gateway</span>
                    <Badge variant="secondary">{session.gateway}</Badge>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Gateway transaction</span>
                    <code className="mt-1 block truncate font-mono text-xs" title={session.gatewayTransactionId}>
                      {session.gatewayTransactionId}
                    </code>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Session expires</span>
                    <p className="mt-1 font-medium">{new Date(session.expiresAt).toLocaleString()}</p>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Mock gateway URL</span>
                    <code className="mt-1 block truncate font-mono text-xs text-muted-foreground" title={session.paymentUrl}>
                      {session.paymentUrl}
                    </code>
                  </div>
                </div>

                <div className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  Đang chờ xác nhận từ gateway...
                </div>

                {actionError && <PaymentAlert message={actionError} />}

                <div className="rounded-lg border border-dashed p-4 text-left">
                  <div className="mb-3 text-sm font-semibold">Điều khiển dành cho showcase</div>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Button onClick={() => void completePayment()} disabled={isBusy} className="flex-1">
                      {completeMutation.isPending ? <LoaderCircle className="animate-spin" /> : <CheckCircle2 />}
                      Mô phỏng thành công
                    </Button>
                    <Button variant="destructive" onClick={() => void failPayment()} disabled={isBusy} className="flex-1">
                      {failMutation.isPending ? <LoaderCircle className="animate-spin" /> : <XCircle />}
                      Mô phỏng thất bại
                    </Button>
                  </div>
                </div>

                <Link to="/shop" className="block">
                  <Button variant="ghost" className="w-full">Tiếp tục mua sắm · Thanh toán sau</Button>
                </Link>
              </CardContent>
            </Card>
          )}
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Tóm tắt đơn hàng</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-3">
                <PackageCheck className="h-5 w-5 text-primary" />
                <div>
                  <div className="font-semibold">{order.orderNumber}</div>
                  <div className="text-xs text-muted-foreground">{order.items?.length ?? 0} dòng sản phẩm</div>
                </div>
              </div>
              <div className="border-t pt-4">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Tổng thanh toán</span>
                  <span className="text-xl font-bold">{formatMoneyMinor(paymentAmount, paymentCurrency)}</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-3 pt-6 text-sm">
              <TrustItem icon={ShieldCheck} text="Backend xác thực lại số tiền và đơn hàng" />
              <TrustItem icon={Clock3} text="PaymentIntent hết hạn sau 30 phút" />
              <TrustItem icon={LockKeyhole} text="Idempotency chống thanh toán trùng" />
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

function UnavailableMethod({ icon: Icon, name }: { icon: ElementType; name: string }) {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-dashed p-4 opacity-55">
      <span className="flex h-11 w-11 items-center justify-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="h-5 w-5" />
      </span>
      <span className="flex-1 font-medium">{name}</span>
      <Badge variant="secondary">Sắp hỗ trợ</Badge>
    </div>
  )
}

function TrustItem({ icon: Icon, text }: { icon: ElementType; text: string }) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-success" />
      <span className="text-muted-foreground">{text}</span>
    </div>
  )
}

function PaymentAlert({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-left text-sm text-destructive">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{message}</span>
    </div>
  )
}

function PaymentError({ title, message }: { title: string; message: string }) {
  return (
    <div className="mx-auto max-w-lg py-16 text-center">
      <AlertCircle className="mx-auto mb-4 h-12 w-12 text-destructive" />
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-muted-foreground">{message}</p>
      <Link to="/orders" className="mt-6 inline-block">
        <Button variant="outline"><ArrowLeft /> Quay lại danh sách đơn</Button>
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
    <div className="mx-auto max-w-xl py-10">
      <Card className="overflow-hidden text-center">
        <div className={cn('h-2', success ? 'bg-success' : 'bg-destructive')} />
        <CardContent className="space-y-6 px-6 py-10">
          <div className={cn(
            'mx-auto flex h-20 w-20 items-center justify-center rounded-full',
            success ? 'bg-success/15 text-success' : 'bg-destructive/10 text-destructive'
          )}>
            {success ? <CheckCircle2 className="h-10 w-10" /> : <XCircle className="h-10 w-10" />}
          </div>
          <div>
            <h1 className="text-3xl font-bold">
              {success ? 'Thanh toán thành công' : expired ? 'Phiên thanh toán đã hết hạn' : 'Thanh toán thất bại'}
            </h1>
            <p className="mt-2 text-muted-foreground">
              {success
                ? `Đã ghi nhận ${amount} cho đơn ${orderNumber ?? orderId}. Đơn hàng vẫn ở trạng thái Pending cho đến khi nhân viên xác nhận.`
                : 'Đơn hàng vẫn được giữ ở trạng thái chờ. Bạn có thể tạo một lần thanh toán mới.'}
            </p>
          </div>

          {error && <PaymentAlert message={error} />}

          <div className="flex flex-col justify-center gap-3 sm:flex-row">
            {!success && onRetry && (
              <Button onClick={onRetry} disabled={retrying}>
                {retrying ? <LoaderCircle className="animate-spin" /> : <RefreshCw />}
                Thử thanh toán lại
              </Button>
            )}
            <Link to="/orders/$id" params={{ id: orderId }}>
              <Button variant={success ? 'default' : 'outline'} className="w-full">
                Xem chi tiết đơn hàng
              </Button>
            </Link>
            {success && (
              <Link to="/shop">
                <Button variant="outline" className="w-full">Tiếp tục mua sắm</Button>
              </Link>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
