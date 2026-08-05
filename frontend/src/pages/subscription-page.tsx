import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Check, CheckCircle2, CreditCard, LockKeyhole, Sparkles } from 'lucide-react'
import {
  MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY,
  useCreateSubscriptionPaymentMutation,
  useMeQuery,
  useMySubscriptionPaymentsQuery,
  useMySubscriptionQuery,
  useSubscriptionPlansQuery,
  type SubscriptionPayment,
  type SubscriptionPlan,
} from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

const T = {
  title: 'G\u00f3i d\u1ecbch v\u1ee5 v\u00e0 thanh to\u00e1n',
  subtitle: 'Ch\u1ecdn g\u00f3i, ho\u00e0n t\u1ea5t thanh to\u00e1n v\u00e0 theo d\u00f5i qu\u00e1 tr\u00ecnh k\u00edch ho\u1ea1t cho doanh nghi\u1ec7p c\u1ee7a b\u1ea1n.',
  loadingAccess: '\u0110ang ki\u1ec3m tra quy\u1ec1n truy c\u1eadp...',
  denied: 'Ch\u1ec9 ch\u1ee7 doanh nghi\u1ec7p c\u00f3 th\u1ec3 qu\u1ea3n l\u00fd g\u00f3i d\u1ecbch v\u1ee5 v\u00e0 thanh to\u00e1n.',
  currentPlan: 'G\u00f3i hi\u1ec7n t\u1ea1i',
  status: 'Tr\u1ea1ng th\u00e1i',
  expiry: 'Ng\u00e0y h\u1ebft h\u1ea1n',
  choosePlan: 'Ch\u1ecdn g\u00f3i ph\u00f9 h\u1ee3p',
  chooseHint: 'G\u00f3i m\u1edbi ch\u1ec9 \u0111\u01b0\u1ee3c k\u00edch ho\u1ea1t sau khi qu\u1ea3n tr\u1ecb vi\u00ean x\u00e1c nh\u1eadn thanh to\u00e1n.',
  monthly: 'Theo th\u00e1ng',
  annual: 'Theo n\u0103m',
  selected: '\u0110\u00e3 ch\u1ecdn',
  choose: 'Ch\u1ecdn g\u00f3i n\u00e0y',
  perMonth: 'm\u1ed7i th\u00e1ng',
  perYear: 'm\u1ed7i n\u0103m',
  continue: 'Ti\u1ebfp t\u1ee5c thanh to\u00e1n',
  creating: '\u0110ang t\u1ea1o \u0111\u01a1n thanh to\u00e1n...',
  upgradeTitle: 'N\u00e2ng c\u1ea5p l\u00ean Premium',
  upgradeHint: (days: number) => `B\u1ea1n ch\u1ec9 thanh to\u00e1n ph\u1ea7n ch\u00eanh l\u1ec7ch cho ${days} ng\u00e0y c\u00f2n l\u1ea1i. Ng\u00e0y h\u1ebft h\u1ea1n hi\u1ec7n t\u1ea1i \u0111\u01b0\u1ee3c gi\u1eef nguy\u00ean.`,
  proratedAmount: 'S\u1ed1 ti\u1ec1n n\u00e2ng c\u1ea5p theo th\u1eddi gian c\u00f2n l\u1ea1i',
  upgrade: 'N\u00e2ng c\u1ea5p v\u00e0 thanh to\u00e1n',
  lockedTitle: 'G\u00f3i d\u1ecbch v\u1ee5 \u0111ang c\u00f3 hi\u1ec7u l\u1ef1c',
  lockedHint: 'B\u1ea1n kh\u00f4ng th\u1ec3 ch\u1ecdn g\u00f3i kh\u00e1c cho \u0111\u1ebfn khi g\u00f3i hi\u1ec7n t\u1ea1i h\u1ebft h\u1ea1n.',
  pendingTitle: 'Thanh to\u00e1n ch\u01b0a ho\u00e0n t\u1ea5t',
  pendingHint: 'Ho\u00e0n t\u1ea5t thanh to\u00e1n hi\u1ec7n t\u1ea1i tr\u01b0\u1edbc khi t\u1ea1o y\u00eau c\u1ea7u m\u1edbi.',
  resume: 'Ti\u1ebfp t\u1ee5c thanh to\u00e1n',
  requote: 'C\u1eadp nh\u1eadt gi\u00e1 n\u00e2ng c\u1ea5p',
  submitted: '\u0110\u00e3 g\u1eedi y\u00eau c\u1ea7u x\u00e1c nh\u1eadn thanh to\u00e1n',
  submittedHint: 'Qu\u1ea3n tr\u1ecb vi\u00ean \u0111ang \u0111\u1ed1i chi\u1ebfu giao d\u1ecbch. G\u00f3i d\u1ecbch v\u1ee5 s\u1ebd \u0111\u01b0\u1ee3c k\u00edch ho\u1ea1t ngay sau khi x\u00e1c nh\u1eadn.',
  history: 'L\u1ecbch s\u1eed thanh to\u00e1n g\u00f3i d\u1ecbch v\u1ee5',
  date: 'Ng\u00e0y t\u1ea1o',
  plan: 'G\u00f3i',
  transaction: 'M\u00e3 giao d\u1ecbch',
  amount: 'S\u1ed1 ti\u1ec1n',
  empty: 'Ch\u01b0a c\u00f3 y\u00eau c\u1ea7u thanh to\u00e1n n\u00e0o.',
  pending: 'Ch\u1edd thanh to\u00e1n',
  awaiting: '\u0110ang ch\u1edd x\u00e1c nh\u1eadn',
  active: '\u0110\u00e3 k\u00edch ho\u1ea1t',
  trial: 'D\u00f9ng th\u1eed',
  pastDue: 'Qu\u00e1 h\u1ea1n thanh to\u00e1n',
  suspended: 'T\u1ea1m ng\u01b0ng',
  expired: 'Hết hạn',
  expiredHint: 'Gói hiện tại đã hết hạn. Bạn có thể gia hạn gói cũ hoặc chọn một gói khác.',
  rejected: 'B\u1ecb t\u1eeb ch\u1ed1i',
  cancelled: '\u0110\u00e3 h\u1ee7y',
  failed: 'Thanh to\u00e1n th\u1ea5t b\u1ea1i',
  createError: 'Kh\u00f4ng th\u1ec3 t\u1ea1o y\u00eau c\u1ea7u thanh to\u00e1n.',
}

const paymentStatuses: Record<SubscriptionPayment['status'], string> = {
  PENDING: T.pending,
  SUBMITTED: T.awaiting,
  APPROVED: T.active,
  REJECTED: T.rejected,
  CANCELLED: T.cancelled,
  FAILED: T.failed,
}

const subscriptionStatuses: Record<string, string> = {
  ACTIVE: T.active,
  TRIAL: T.trial,
  PAST_DUE: T.pastDue,
  SUSPENDED: T.suspended,
  EXPIRED: T.expired,
  CANCELLED: T.cancelled,
}

function getErrorMessage(cause: unknown, fallback: string) {
  if (cause instanceof Error) return cause.message
  if (typeof cause === 'object' && cause !== null && 'error' in cause && typeof cause.error === 'string') return cause.error
  return fallback
}

function PlanFeatures({ plan }: { plan: SubscriptionPlan }) {
  return <div className="space-y-2 text-sm">
    {Object.entries(plan.features)
      .filter(([, enabled]) => enabled)
      .slice(0, 6)
      .map(([feature]) => <p key={feature} className="flex gap-2"><Check className="h-4 w-4 text-success" />{feature}</p>)}
  </div>
}

export function SubscriptionPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { data: me, isLoading: meLoading } = useMeQuery()
  const { data: plans = [], isLoading: plansLoading } = useSubscriptionPlansQuery()
  const { data: subscription } = useMySubscriptionQuery()
  const { data: payments = [], isLoading: paymentsLoading } = useMySubscriptionPaymentsQuery()
  const createPayment = useCreateSubscriptionPaymentMutation()
  const [period, setPeriod] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY')
  const [planCode, setPlanCode] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (subscription?.status !== 'EXPIRED' || planCode) return
    setPlanCode(subscription.planCode)
    if (subscription.billingPeriod === 'MONTHLY' || subscription.billingPeriod === 'ANNUAL') {
      setPeriod(subscription.billingPeriod)
    }
  }, [subscription, planCode])

  const outstandingPayment = payments.find((payment) => payment.status === 'SUBMITTED')
    ?? payments.find((payment) => payment.status === 'PENDING')
  const periodEnd = subscription?.currentPeriodEndsAt ? new Date(subscription.currentPeriodEndsAt) : undefined
  const hasActivePaidTerm = Boolean(
    subscription?.status === 'ACTIVE'
    && subscription.priceMinor > 0
    && periodEnd
    && periodEnd.getTime() > Date.now(),
  )
  const upgradeQuote = hasActivePaidTerm ? subscription?.upgradeQuote : undefined
  const purchasePeriod = upgradeQuote?.billingPeriod ?? period
  const selectedPlan = upgradeQuote
    ? plans.find((plan) => plan.code === upgradeQuote.toPlanCode)
    : plans.find((plan) => plan.code === planCode) ?? plans[0]
  const showPlanPicker = !outstandingPayment && !hasActivePaidTerm
  const showUpgrade = !outstandingPayment && Boolean(upgradeQuote)
  const showLocked = !outstandingPayment && hasActivePaidTerm && !upgradeQuote
  const pendingNeedsRequote = outstandingPayment?.status === 'PENDING' && Boolean(upgradeQuote) && (
    outstandingPayment.planCode !== upgradeQuote?.toPlanCode
    || outstandingPayment.billingPeriod !== upgradeQuote?.billingPeriod
    || outstandingPayment.amountMinor !== upgradeQuote?.amountMinor
  )
  const step = outstandingPayment?.status === 'PENDING' ? 2 : outstandingPayment?.status === 'SUBMITTED' || hasActivePaidTerm ? 3 : 1

  const refresh = () => queryClient.invalidateQueries({ queryKey: MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY })
  const create = async () => {
    if (!selectedPlan) return
    setError('')
    try {
      const payment = await createPayment.mutateAsync({
        planCode: selectedPlan.code,
        billingPeriod: purchasePeriod,
        idempotencyKey: crypto.randomUUID(),
      })
      await refresh()
      await navigate({ to: '/subscription/checkout/$id', params: { id: payment.id } })
    } catch (cause) {
      setError(getErrorMessage(cause, T.createError))
    }
  }

  if (meLoading) return <p className="py-8 text-center text-muted-foreground">{T.loadingAccess}</p>
  if (me?.role?.name !== 'businessOwner') return <p className="py-8 text-center text-destructive">{T.denied}</p>

  return <div className="mx-auto max-w-6xl space-y-6 pb-12">
    <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-5">
      <div className="flex items-start gap-3">
        <CreditCard className="mt-1 h-7 w-7 text-primary" />
        <div><h1 className="text-3xl font-bold tracking-tight">{T.title}</h1><p className="mt-1 text-muted-foreground">{T.subtitle}</p></div>
      </div>
      <div className="flex gap-2 text-xs font-semibold text-muted-foreground">
        <span className={step >= 1 ? 'text-primary' : ''}>1. {T.choosePlan.toUpperCase()}</span><span>/</span>
        <span className={step >= 2 ? 'text-primary' : ''}>2. {T.pending.toUpperCase()}</span><span>/</span>
        <span className={step >= 3 ? 'text-primary' : ''}>3. {T.status.toUpperCase()}</span>
      </div>
    </div>

    {subscription && <Card><CardContent className="flex flex-wrap gap-x-8 gap-y-3 pt-5">
      <div><p className="text-sm text-muted-foreground">{T.currentPlan}</p><p className="font-semibold">{subscription.planName}</p></div>
      <div><p className="text-sm text-muted-foreground">{T.status}</p><Badge variant={subscription.readOnly ? 'destructive' : 'default'}>{subscriptionStatuses[subscription.status] ?? subscription.status}</Badge></div>
      {periodEnd && <div><p className="text-sm text-muted-foreground">{T.expiry}</p><p className="font-semibold">{periodEnd.toLocaleDateString('vi-VN')}</p></div>}
    </CardContent></Card>}

    {showPlanPicker && <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><h2 className="text-xl font-semibold">{T.choosePlan}</h2><p className="text-sm text-muted-foreground">{subscription?.status === 'EXPIRED' ? T.expiredHint : T.chooseHint}</p></div>
        <Select value={period} onValueChange={(value) => setPeriod(value as 'MONTHLY' | 'ANNUAL')}>
          <SelectTrigger className="w-36"><SelectValue /></SelectTrigger>
          <SelectContent><SelectItem value="MONTHLY">{T.monthly}</SelectItem><SelectItem value="ANNUAL">{T.annual}</SelectItem></SelectContent>
        </Select>
      </div>
      {plansLoading ? <p className="py-6 text-center text-muted-foreground">{T.loadingAccess}</p> : <div className="grid gap-5 lg:grid-cols-2">
        {plans.map((plan) => {
          const selected = plan.code === selectedPlan?.code
          const price = period === 'ANNUAL' ? plan.annualPriceMinor : plan.monthlyPriceMinor
          return <Card key={plan.code} className={selected ? 'border-primary ring-1 ring-primary' : ''}>
            <CardHeader><CardTitle className="flex justify-between">{plan.name}{selected && <Check className="h-5 w-5 text-primary" />}</CardTitle></CardHeader>
            <CardContent className="space-y-5">
              <div><p className="text-3xl font-bold">{formatMoneyMinor(price, plan.currency)}</p><p className="text-sm text-muted-foreground">{period === 'ANNUAL' ? T.perYear : T.perMonth}</p></div>
              <PlanFeatures plan={plan} />
              <Button className="w-full" variant={selected ? 'default' : 'outline'} onClick={() => setPlanCode(plan.code)}>{selected ? T.selected : T.choose}</Button>
            </CardContent>
          </Card>
        })}
      </div>}
      <div className="flex justify-end"><Button onClick={create} disabled={!selectedPlan || createPayment.isPending}><LockKeyhole className="mr-2 h-4 w-4" />{createPayment.isPending ? T.creating : T.continue}</Button></div>
    </>}

    {showUpgrade && upgradeQuote && selectedPlan && <Card className="border-primary/40">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-primary" />{T.upgradeTitle}</CardTitle>
        <p className="text-sm text-muted-foreground">{T.upgradeHint(upgradeQuote.remainingDays)}</p>
      </CardHeader>
      <CardContent className="grid gap-6 md:grid-cols-[1fr_auto] md:items-end">
        <div className="space-y-5">
          <div><p className="text-sm text-muted-foreground">{T.proratedAmount}</p><p className="text-3xl font-bold">{formatMoneyMinor(upgradeQuote.amountMinor, upgradeQuote.currency)}</p></div>
          <PlanFeatures plan={selectedPlan} />
        </div>
        <Button onClick={create} disabled={createPayment.isPending}><LockKeyhole className="mr-2 h-4 w-4" />{createPayment.isPending ? T.creating : T.upgrade}</Button>
      </CardContent>
    </Card>}

    {showLocked && <Card className="border-primary/30 bg-primary/5"><CardContent className="flex items-center gap-4 py-6">
      <LockKeyhole className="h-9 w-9 text-primary" />
      <div><h2 className="font-semibold">{T.lockedTitle}</h2><p className="mt-1 text-sm text-muted-foreground">{T.lockedHint}</p></div>
    </CardContent></Card>}

    {outstandingPayment && <Card className="border-primary/30 bg-primary/5"><CardContent className="flex flex-wrap items-center justify-between gap-4 py-6">
      <div className="flex items-center gap-4"><CheckCircle2 className="h-9 w-9 text-primary" /><div><h2 className="font-semibold">{outstandingPayment.status === 'SUBMITTED' ? T.submitted : T.pendingTitle}</h2><p className="mt-1 text-sm text-muted-foreground">{outstandingPayment.status === 'SUBMITTED' ? T.submittedHint : T.pendingHint}</p></div></div>
      {outstandingPayment.status === 'PENDING' && <Button onClick={pendingNeedsRequote ? create : () => navigate({ to: '/subscription/checkout/$id', params: { id: outstandingPayment.id } })} disabled={createPayment.isPending}>{pendingNeedsRequote ? T.requote : T.resume}</Button>}
    </CardContent></Card>}

    {error && <p className="text-sm text-destructive">{error}</p>}

    <Card><CardHeader><CardTitle>{T.history}</CardTitle></CardHeader><CardContent>
      {paymentsLoading ? <p className="text-sm text-muted-foreground">{T.loadingAccess}</p> : payments.length === 0 ? <p className="py-5 text-center text-sm text-muted-foreground">{T.empty}</p> : <Table>
        <TableHeader><TableRow><TableHead>{T.date}</TableHead><TableHead>{T.plan}</TableHead><TableHead>{T.transaction}</TableHead><TableHead>{T.status}</TableHead><TableHead className="text-right">{T.amount}</TableHead></TableRow></TableHeader>
        <TableBody>{payments.map((payment) => <TableRow key={payment.id}>
          <TableCell>{payment.createdAt ? new Date(payment.createdAt).toLocaleString('vi-VN') : '\u2014'}</TableCell>
          <TableCell>{payment.planCode}{' \u00b7 '}{payment.billingPeriod === 'ANNUAL' ? T.annual : T.monthly}</TableCell>
          <TableCell>{payment.paymentReference ?? '\u2014'}</TableCell>
          <TableCell><Badge variant={payment.status === 'REJECTED' ? 'destructive' : payment.status === 'APPROVED' ? 'default' : 'secondary'}>{paymentStatuses[payment.status]}</Badge>{payment.rejectionReason && <p className="mt-1 text-xs text-destructive">{payment.rejectionReason}</p>}</TableCell>
          <TableCell className="text-right font-semibold">{formatMoneyMinor(payment.amountMinor, payment.currency)}</TableCell>
        </TableRow>)}</TableBody>
      </Table>}
    </CardContent></Card>
  </div>
}
