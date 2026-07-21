import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useGetDailyRevenueQuery, useGetMonthToDateRevenueQuery, type DailyRevenue } from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import {
  CalendarDays,
  ChartNoAxesCombined,
  CircleDollarSign,
  HandCoins,
  PackageOpen,
  ReceiptText,
  RotateCcw,
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
    </div>
  )
}
