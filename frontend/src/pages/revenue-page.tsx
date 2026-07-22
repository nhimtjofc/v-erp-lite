import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useCreateRevenueAdjustmentMutation,
  useDeleteRevenueAdjustmentMutation,
  useGetDailyRevenueQuery,
  useGetMonthlyRevenueQuery,
  useGetMonthToDateRevenueQuery,
  useMeQuery,
  type DailyRevenue,
} from '@/shared/api'
import { formatMoneyMinor, parseMoneyToMinor } from '@/shared/utils/currency'
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
import {
  CalendarDays,
  ChartNoAxesCombined,
  CircleDollarSign,
  HandCoins,
  ListChecks,
  PackageOpen,
  Plus,
  ReceiptText,
  RotateCcw,
  Trash2,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'

const CURRENCY = 'VND'

function toDateInput(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function monthStart(): string {
  const today = new Date()
  return toDateInput(new Date(today.getFullYear(), today.getMonth(), 1))
}

function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return toDateInput(date)
}

function formatDate(value: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
  }).format(new Date(`${value}T00:00:00`))
}

interface SummaryCardProps {
  label: string
  value: string
  hint: string
  icon: typeof CircleDollarSign
  tone?: 'default' | 'danger' | 'success'
}

function SummaryCard({ label, value, hint, icon: Icon, tone = 'default' }: SummaryCardProps) {
  const toneClass = tone === 'danger'
    ? 'text-destructive'
    : tone === 'success'
      ? 'text-success'
      : 'text-primary'

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className={`h-4 w-4 ${toneClass}`} />
      </CardHeader>
      <CardContent>
        <div className={`text-2xl font-bold tracking-tight ${toneClass}`}>{value}</div>
        <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  )
}

function DailyBars({ days, locale }: { days: DailyRevenue[]; locale: string }) {
  const { t } = useTranslation('revenue')
  const visibleDays = days.slice(-31)
  const maxValue = Math.max(1, ...visibleDays.map((day) => Math.abs(Number(day.profitMinor ?? 0))))

  return (
    <div className="flex h-56 items-end gap-1 overflow-x-auto border-b border-border pb-7 pt-4">
      {visibleDays.map((day) => {
        const profit = Number(day.profitMinor ?? 0)
        const height = profit === 0 ? 3 : Math.max(8, Math.round((Math.abs(profit) / maxValue) * 168))
        return (
          <div key={day.date} className="group relative flex min-w-7 flex-1 flex-col items-center justify-end">
            <div className="pointer-events-none absolute bottom-full z-10 mb-2 hidden w-48 rounded-lg border bg-popover p-3 text-xs shadow-lg group-hover:block">
              <p className="font-semibold">{formatDate(day.date!, locale)}</p>
              <p className={profit >= 0 ? 'mt-1 text-success' : 'mt-1 text-destructive'}>
                {t('profit')}: {formatMoneyMinor(profit, CURRENCY)}
              </p>
              <p className="text-muted-foreground">{t('netRevenue')}: {formatMoneyMinor(Number(day.netRevenueMinor ?? 0), CURRENCY)}</p>
              <p className="text-muted-foreground">{t('warehouseCost')}: {formatMoneyMinor(Number(day.warehouseCostMinor ?? 0), CURRENCY)}</p>
            </div>
            <div
              className={`w-full max-w-8 rounded-t transition-colors ${
                profit >= 0 ? 'bg-success/80 group-hover:bg-success' : 'bg-destructive/80 group-hover:bg-destructive'
              }`}
              style={{ height }}
              aria-label={`${day.date}: ${formatMoneyMinor(profit, CURRENCY)}`}
            />
            <span className="absolute top-full mt-2 text-[10px] text-muted-foreground">
              {new Date(`${day.date}T00:00:00`).getDate()}
            </span>
          </div>
        )
      })}
    </div>
  )
}

const ADJUSTMENT_CATEGORIES = [
  'OTHER_INCOME',
  'OPERATING_EXPENSE',
  'WAREHOUSE_ADJUSTMENT',
  'COMMISSION_ADJUSTMENT',
  'RETURN_ADJUSTMENT',
] as const

function AdjustmentDialog({
  open,
  onOpenChange,
  today,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  today: string
  onSaved: () => Promise<unknown>
}) {
  const { t } = useTranslation('revenue')
  const createAdjustment = useCreateRevenueAdjustmentMutation()
  const [entryDate, setEntryDate] = useState(today)
  const [category, setCategory] = useState<(typeof ADJUSTMENT_CATEGORIES)[number]>('OPERATING_EXPENSE')
  const [direction, setDirection] = useState<'INCOME' | 'EXPENSE'>('EXPENSE')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')

  const handleCategoryChange = (value: string | null) => {
    if (!value) return
    const nextCategory = value as (typeof ADJUSTMENT_CATEGORIES)[number]
    setCategory(nextCategory)
    if (nextCategory === 'OTHER_INCOME') setDirection('INCOME')
    if (nextCategory === 'OPERATING_EXPENSE') setDirection('EXPENSE')
  }

  const save = async () => {
    const amountMinor = parseMoneyToMinor(amount, CURRENCY)
    if (!entryDate || amountMinor == null) {
      setError(t('adjustmentValidation'))
      return
    }
    try {
      await createAdjustment.mutateAsync({
        body: {
          entryDate,
          category,
          direction,
          amountMinor,
          currency: CURRENCY,
          description: description.trim() || undefined,
        },
      })
      await onSaved()
      setAmount('')
      setDescription('')
      setError('')
      onOpenChange(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('adjustmentSaveError'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('addAdjustment')}</DialogTitle>
          <DialogDescription>{t('addAdjustmentHint')}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="adjustment-date">{t('date')}</Label>
            <Input
              id="adjustment-date"
              type="date"
              value={entryDate}
              max={today}
              onChange={(event) => setEntryDate(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="adjustment-category">{t('category')}</Label>
            <Select value={category} onValueChange={handleCategoryChange}>
              <SelectTrigger id="adjustment-category"><SelectValue /></SelectTrigger>
              <SelectContent>
                {ADJUSTMENT_CATEGORIES.map((value) => (
                  <SelectItem key={value} value={value}>{t(`breakdown.${value}`)}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="adjustment-direction">{t('direction')}</Label>
            <Select value={direction} onValueChange={(value) => value && setDirection(value as 'INCOME' | 'EXPENSE')}>
              <SelectTrigger id="adjustment-direction"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="INCOME">{t('income')}</SelectItem>
                <SelectItem value="EXPENSE">{t('expense')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="adjustment-amount">{t('amount')}</Label>
            <Input
              id="adjustment-amount"
              type="number"
              min="1"
              step="1"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="0"
            />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="adjustment-description">{t('description')}</Label>
            <Input
              id="adjustment-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder={t('descriptionPlaceholder')}
            />
          </div>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>{t('cancel')}</Button>
          <Button onClick={save} disabled={createAdjustment.isPending}>
            {createAdjustment.isPending ? t('saving') : t('saveAdjustment')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function MonthlyReportSection({ currentMonth, today }: { currentMonth: string; today: string }) {
  const { t, i18n } = useTranslation('revenue')
  const [month, setMonth] = useState(currentMonth)
  const [adjustmentOpen, setAdjustmentOpen] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const { data: me } = useMeQuery()
  const canEdit = me?.role?.name === 'admin' || me?.role?.name === 'businessOwner'
  const monthlyQuery = useGetMonthlyRevenueQuery({
    query: { month, currency: CURRENCY },
  })
  const deleteAdjustment = useDeleteRevenueAdjustmentMutation()
  const report = monthlyQuery.data
  const profit = Number(report?.totals?.profitMinor ?? 0)

  const removeAdjustment = async (id?: string) => {
    if (!id || !window.confirm(t('deleteAdjustmentConfirm'))) return
    try {
      setDeleteError('')
      await deleteAdjustment.mutateAsync({ path: { id } })
      await monthlyQuery.refetch()
    } catch (cause) {
      setDeleteError(cause instanceof Error ? cause.message : t('adjustmentDeleteError'))
    }
  }

  return (
    <>
      <Card className="overflow-hidden">
        <CardHeader className="border-b bg-muted/30">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div>
              <CardTitle className="flex items-center gap-2">
                <ListChecks className="h-5 w-5 text-primary" /> {t('monthlyReport')}
              </CardTitle>
              <p className="mt-1 text-sm text-muted-foreground">{t('monthlyReportHint')}</p>
            </div>
            <div className="flex items-center gap-2">
              <Input
                aria-label={t('reportMonth')}
                type="month"
                value={month}
                max={currentMonth}
                onChange={(event) => setMonth(event.target.value)}
                className="w-40"
              />
              {canEdit && (
                <Button onClick={() => setAdjustmentOpen(true)}>
                  <Plus className="h-4 w-4" /> {t('addAdjustment')}
                </Button>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6 pt-6">
          {monthlyQuery.isError && (
            <p className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              {t('monthlyLoadError')}
            </p>
          )}

          <div className={`rounded-xl border p-5 ${profit >= 0 ? 'border-success/30 bg-success/10' : 'border-destructive/30 bg-destructive/10'}`}>
            <p className="text-sm font-medium text-muted-foreground">{profit >= 0 ? t('monthProfit') : t('monthLoss')}</p>
            <div className="mt-1 flex items-center gap-2">
              {profit >= 0 ? <TrendingUp className="h-6 w-6 text-success" /> : <TrendingDown className="h-6 w-6 text-destructive" />}
              <p className={`text-3xl font-bold ${profit >= 0 ? 'text-success' : 'text-destructive'}`}>
                {monthlyQuery.isLoading ? '…' : formatMoneyMinor(profit, CURRENCY)}
              </p>
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              {report?.from} — {report?.to} · {report?.timezone ?? 'Asia/Ho_Chi_Minh'}
            </p>
          </div>

          <div>
            <h3 className="mb-3 text-sm font-semibold">{t('profitBreakdown')}</h3>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {(report?.breakdown ?? []).map((item) => {
                const isIncome = item.direction === 'INCOME'
                return (
                  <div key={`${item.code}-${item.direction}`} className="flex items-center justify-between rounded-lg border p-3">
                    <div>
                      <p className="text-sm font-medium">{t(`breakdown.${item.code}`)}</p>
                      <p className="text-xs text-muted-foreground">{isIncome ? t('income') : t('expense')}</p>
                    </div>
                    <p className={`font-semibold ${isIncome ? 'text-success' : 'text-destructive'}`}>
                      {isIncome ? '+' : '-'}{formatMoneyMinor(Number(item.amountMinor ?? 0), CURRENCY)}
                    </p>
                  </div>
                )
              })}
            </div>
          </div>

          <div>
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-sm font-semibold">{t('adjustmentLedger')}</h3>
              <Badge variant="outline">{t('entries', { count: report?.adjustments?.length ?? 0 })}</Badge>
            </div>
            {deleteError && <p className="mb-3 text-sm text-destructive">{deleteError}</p>}
            <div className="overflow-x-auto rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('date')}</TableHead>
                    <TableHead>{t('category')}</TableHead>
                    <TableHead>{t('description')}</TableHead>
                    <TableHead>{t('direction')}</TableHead>
                    <TableHead className="text-right">{t('amount')}</TableHead>
                    {canEdit && <TableHead className="w-12" />}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {monthlyQuery.isLoading ? (
                    <TableRow><TableCell colSpan={canEdit ? 6 : 5} className="py-8 text-center text-muted-foreground">{t('loading')}</TableCell></TableRow>
                  ) : (report?.adjustments?.length ?? 0) === 0 ? (
                    <TableRow><TableCell colSpan={canEdit ? 6 : 5} className="py-8 text-center text-muted-foreground">{t('noAdjustments')}</TableCell></TableRow>
                  ) : report?.adjustments?.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>{entry.entryDate ? formatDate(entry.entryDate, i18n.language) : '—'}</TableCell>
                      <TableCell className="font-medium">{t(`breakdown.${entry.category}`)}</TableCell>
                      <TableCell className="max-w-72 truncate text-muted-foreground">{entry.description || '—'}</TableCell>
                      <TableCell>
                        <Badge variant={entry.direction === 'INCOME' ? 'default' : 'secondary'}>
                          {entry.direction === 'INCOME' ? t('income') : t('expense')}
                        </Badge>
                      </TableCell>
                      <TableCell className={`text-right font-semibold ${entry.direction === 'INCOME' ? 'text-success' : 'text-destructive'}`}>
                        {entry.direction === 'INCOME' ? '+' : '-'}{formatMoneyMinor(Number(entry.amountMinor ?? 0), entry.currency ?? CURRENCY)}
                      </TableCell>
                      {canEdit && (
                        <TableCell>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => removeAdjustment(entry.id)}
                            disabled={deleteAdjustment.isPending}
                            aria-label={t('deleteAdjustment')}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
        </CardContent>
      </Card>

      <AdjustmentDialog
        open={adjustmentOpen}
        onOpenChange={setAdjustmentOpen}
        today={today}
        onSaved={() => monthlyQuery.refetch()}
      />
    </>
  )
}

export function RevenuePage() {
  const { t, i18n } = useTranslation('revenue')
  const today = toDateInput(new Date())
  const [from, setFrom] = useState(monthStart)
  const [to, setTo] = useState(today)
  const dateRangeInvalid = from > to
  const currentMonth = today.slice(0, 7)

  const { data, isLoading, isError, refetch } = useGetDailyRevenueQuery({
    query: {
      from,
      to,
      currency: CURRENCY,
    },
  })
  const { data: monthToDate, isLoading: isMonthToDateLoading } = useGetMonthToDateRevenueQuery({
    query: {
      month: currentMonth,
      currency: CURRENCY,
    },
  })

  const days = useMemo(() => data?.days ?? [], [data?.days])
  const totals = data?.totals

  const applyPreset = (preset: 'month' | 'sevenDays') => {
    setFrom(preset === 'month' ? monthStart() : daysAgo(6))
    setTo(today)
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <div className="flex items-center gap-2">
            <ChartNoAxesCombined className="h-7 w-7 text-primary" />
            <h1 className="text-3xl font-bold tracking-tight">{t('title')}</h1>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">{t('subtitle')}</p>
        </div>

        <div className="flex flex-col gap-3 rounded-xl border bg-card p-3 sm:flex-row sm:items-end">
          <div>
            <label htmlFor="revenue-from" className="mb-1 block text-xs font-medium text-muted-foreground">
              {t('fromDate')}
            </label>
            <Input
              id="revenue-from"
              type="date"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="revenue-to" className="mb-1 block text-xs font-medium text-muted-foreground">
              {t('toDate')}
            </label>
            <Input
              id="revenue-to"
              type="date"
              value={to}
              min={from}
              max={today}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => applyPreset('sevenDays')}>
              {t('lastSevenDays')}
            </Button>
            <Button variant="outline" size="sm" onClick={() => applyPreset('month')}>
              {t('thisMonth')}
            </Button>
          </div>
        </div>
      </div>

      {dateRangeInvalid && (
        <p className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          {t('invalidDateRange')}
        </p>
      )}

      {isError && (
        <div className="flex items-center justify-between rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          <span>{t('loadError')}</span>
          <Button variant="outline" size="sm" onClick={() => refetch()}>{t('retry')}</Button>
        </div>
      )}

      <Card className="overflow-hidden border-primary/20">
        <CardHeader className="bg-primary/5">
          <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
            <div>
              <CardTitle>{t('monthToDate')}</CardTitle>
              <p className="mt-1 text-sm text-muted-foreground">{t('monthToDateHint')}</p>
            </div>
            <span className="w-fit rounded-full border bg-background px-3 py-1 text-xs font-medium">
              {monthToDate?.from} — {monthToDate?.to}
            </span>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4 pt-6 sm:grid-cols-2 xl:grid-cols-4">
          {[
            {
              label: t('netRevenue'),
              value: Number(monthToDate?.totals?.netRevenueMinor ?? 0),
              icon: CircleDollarSign,
              className: 'text-primary',
            },
            {
              label: t('commission'),
              value: Number(monthToDate?.totals?.commissionMinor ?? 0),
              icon: HandCoins,
              className: 'text-amber-600 dark:text-amber-400',
            },
            {
              label: t('warehouseCost'),
              value: Number(monthToDate?.totals?.warehouseCostMinor ?? 0),
              icon: PackageOpen,
              className: 'text-violet-600 dark:text-violet-400',
            },
            {
              label: Number(monthToDate?.totals?.profitMinor ?? 0) >= 0 ? t('profit') : t('loss'),
              value: Number(monthToDate?.totals?.profitMinor ?? 0),
              icon: Number(monthToDate?.totals?.profitMinor ?? 0) >= 0 ? TrendingUp : TrendingDown,
              className: Number(monthToDate?.totals?.profitMinor ?? 0) >= 0 ? 'text-success' : 'text-destructive',
            },
          ].map((item) => {
            const Icon = item.icon
            return (
              <div key={item.label} className="rounded-lg border bg-background p-4">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-medium text-muted-foreground">{item.label}</p>
                  <Icon className={`h-4 w-4 ${item.className}`} />
                </div>
                <p className={`mt-2 text-xl font-bold ${item.className}`}>
                  {isMonthToDateLoading ? '…' : formatMoneyMinor(item.value, CURRENCY)}
                </p>
              </div>
            )
          })}
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          label={t('grossRevenue')}
          value={isLoading ? '…' : formatMoneyMinor(Number(totals?.grossRevenueMinor ?? 0), CURRENCY)}
          hint={t('grossRevenueHint')}
          icon={CircleDollarSign}
        />
        <SummaryCard
          label={t('returns')}
          value={isLoading ? '…' : formatMoneyMinor(Number(totals?.refundMinor ?? 0), CURRENCY)}
          hint={t('returnsHint', { count: Number(totals?.refundCount ?? 0) })}
          icon={RotateCcw}
          tone="danger"
        />
        <SummaryCard
          label={t('netRevenue')}
          value={isLoading ? '…' : formatMoneyMinor(Number(totals?.netRevenueMinor ?? 0), CURRENCY)}
          hint={t('netRevenueHint')}
          icon={ChartNoAxesCombined}
          tone="success"
        />
        <SummaryCard
          label={t('completedOrders')}
          value={isLoading ? '…' : Number(totals?.orderCount ?? 0).toLocaleString(i18n.language)}
          hint={t('completedOrdersHint')}
          icon={ReceiptText}
        />
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <CalendarDays className="h-5 w-5 text-primary" /> {t('dailyProfitChart')}
            </CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">{t('dailyProfitChartHint')}</p>
          </div>
          <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
            {data?.timezone ?? 'Asia/Ho_Chi_Minh'}
          </span>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="h-56 animate-pulse rounded-lg bg-muted" />
          ) : (
            <DailyBars days={days} locale={i18n.language} />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('dailyDetails')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('date')}</TableHead>
                  <TableHead className="text-right">{t('orders')}</TableHead>
                  <TableHead className="text-right">{t('grossRevenue')}</TableHead>
                  <TableHead className="text-right">{t('returns')}</TableHead>
                  <TableHead className="text-right">{t('commission')}</TableHead>
                  <TableHead className="text-right">{t('warehouseCost')}</TableHead>
                  <TableHead className="text-right">{t('netRevenue')}</TableHead>
                  <TableHead className="text-right">{t('profitLoss')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  <TableRow><TableCell colSpan={8} className="py-8 text-center text-muted-foreground">{t('loading')}</TableCell></TableRow>
                ) : days.length === 0 ? (
                  <TableRow><TableCell colSpan={8} className="py-8 text-center text-muted-foreground">{t('noData')}</TableCell></TableRow>
                ) : days.map((day) => (
                  <TableRow key={day.date}>
                    <TableCell className="font-medium">{formatDate(day.date!, i18n.language)}</TableCell>
                    <TableCell className="text-right">{Number(day.orderCount ?? 0)}</TableCell>
                    <TableCell className="text-right">{formatMoneyMinor(Number(day.grossRevenueMinor ?? 0), CURRENCY)}</TableCell>
                    <TableCell className="text-right text-destructive">-{formatMoneyMinor(Number(day.refundMinor ?? 0), CURRENCY)}</TableCell>
                    <TableCell className="text-right">{formatMoneyMinor(Number(day.commissionMinor ?? 0), CURRENCY)}</TableCell>
                    <TableCell className="text-right">{formatMoneyMinor(Number(day.warehouseCostMinor ?? 0), CURRENCY)}</TableCell>
                    <TableCell className="text-right font-semibold text-success">{formatMoneyMinor(Number(day.netRevenueMinor ?? 0), CURRENCY)}</TableCell>
                    <TableCell className={`text-right font-bold ${Number(day.profitMinor ?? 0) >= 0 ? 'text-success' : 'text-destructive'}`}>
                      {formatMoneyMinor(Number(day.profitMinor ?? 0), CURRENCY)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      <MonthlyReportSection currentMonth={currentMonth} today={today} />
    </div>
  )
}
